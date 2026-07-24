package com.azuredoom.hytalepublisher

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Publishing creates a GitHub release/tag and uploads assets, and should always execute when requested.")
abstract class GitHubPublishTask extends AbstractPublishTask {

	@Internal
	GitHubConfig githubConfig

	@Internal
	abstract RegularFileProperty getSourcesJarFile()

	@Internal
	abstract RegularFileProperty getJavadocJarFile()

	@TaskAction
	void publish() {
		def cfg = githubConfig

		def token = credentials().require(cfg.apiKeyProp, cfg.apiKeyEnv, "GitHub token")
		def repo  = resolveRepository(cfg)
		def curl  = curlExe()

		def tagName      = "${cfg.tagPrefix}${projectVersion.get()}"
		def commitish    = resolveCommitish(cfg)
		def releaseName  = cfg.releaseName?.trim() ? cfg.releaseName : "${projectName.get()} ${projectVersion.get()}"
		def body         = readChangelog()
		def prerelease   = cfg.prerelease || (cfg.autoPrerelease && releaseType.get().toLowerCase() != "release")

		def payload = [
			tag_name              : tagName,
			target_commitish      : commitish,
			name                  : releaseName,
			body                  : body,
			draft                 : cfg.draft,
			prerelease            : prerelease,
			generate_release_notes: cfg.generateReleaseNotes,
			make_latest           : cfg.makeLatest
		]

		if (cfg.discussionCategoryName?.trim()) {
			payload.discussion_category_name = cfg.discussionCategoryName
		}

		def releaseJson = createRelease(curl, cfg, repo, token, payload)
		def releaseId   = releaseJson.id
		def htmlUrl     = releaseJson.html_url

		def assets = collectAssets(cfg)

		assets.each { file ->
			uploadAsset(curl, cfg, repo, token, releaseId, file)
			logger.lifecycle("[HytalePublisher] Uploaded asset to GitHub release: ${file.name}")
		}

		logger.lifecycle("[HytalePublisher] Successfully published GitHub release ${tagName} for ${projectName.get()} ${projectVersion.get()}: ${htmlUrl}")
	}

	private Map createRelease(String curl, GitHubConfig cfg, String repo, String token, Map payload) {
		def json         = JsonOutput.toJson(payload)
		def payloadFile  = File.createTempFile("hytalepublisher-github-release-", ".json")
		payloadFile.text = json

		def args = [
			curl,
			"-sS",
			"-X",
			"POST",
			"${cfg.apiBaseUrl}/repos/${repo}/releases",
			"-H",
			"Authorization: Bearer ${token}",
			"-H",
			"Accept: application/vnd.github+json",
			"-H",
			"X-GitHub-Api-Version: 2022-11-28",
			"-H",
			"Content-Type: application/json",
			"--data-binary",
			"@${payloadFile.absolutePath}",
			"-w",
			"\n%{http_code}"
		]

		def response = execCapture(args)
		def (body, httpCode) = splitHttpResponse(response)

		if (!httpCode.startsWith("2")) {
			throw new GradleException(
			"[HytalePublisher] GitHub release creation failed with HTTP ${httpCode}.\n\n${body}"
			)
		}

		return new JsonSlurper().parseText(body) as Map
	}

	protected void uploadAsset(String curl, GitHubConfig cfg, String repo, String token, def releaseId, File file) {
		def encodedName = URLEncoder.encode(file.name, "UTF-8")
		def uploadUrl   = "${cfg.uploadBaseUrl}/repos/${repo}/releases/${releaseId}/assets?name=${encodedName}"

		def args = [
			curl,
			"-sS",
			"-X",
			"POST",
			uploadUrl,
			"-H",
			"Authorization: Bearer ${token}",
			"-H",
			"Accept: application/vnd.github+json",
			"-H",
			"X-GitHub-Api-Version: 2022-11-28",
			"-H",
			"Content-Type: ${mimeTypeFor(file)}",
			"--data-binary",
			"@${file.absolutePath}",
			"-w",
			"\n%{http_code}"
		]

		def response = execCapture(args)
		def (body, httpCode) = splitHttpResponse(response)

		if (!httpCode.startsWith("2")) {
			throw new GradleException(
			"[HytalePublisher] Uploading '${file.name}' to the GitHub release failed with HTTP ${httpCode}.\n\n${body}"
			)
		}
	}

	private List<String> splitHttpResponse(String response) {
		def lines    = response.trim().split('\n') as List
		def httpCode = lines.last().trim()
		def body     = lines.dropRight(1).join('\n').trim()
		return [body, httpCode]
	}

	private List<File> collectAssets(GitHubConfig cfg) {
		def files = []

		if (cfg.includeJar) {
			files << resolveJar()
		}

		if (cfg.includeSourcesJar) {
			if (sourcesJarFile.isPresent()) {
				def file = sourcesJarFile.get().asFile
				if (file.exists()) {
					files << file
				} else {
					logger.warn("[HytalePublisher] github.includeSourcesJar is true but the sources jar was not found: ${file.absolutePath}")
				}
			} else {
				logger.warn("[HytalePublisher] github.includeSourcesJar is true but no '${cfg.sourcesJarTaskName}' task output was wired up.")
			}
		}

		if (cfg.includeJavadocJar) {
			if (javadocJarFile.isPresent()) {
				def file = javadocJarFile.get().asFile
				if (file.exists()) {
					files << file
				} else {
					logger.warn("[HytalePublisher] github.includeJavadocJar is true but the javadoc jar was not found: ${file.absolutePath}")
				}
			} else {
				logger.warn("[HytalePublisher] github.includeJavadocJar is true but no '${cfg.javadocJarTaskName}' task output was wired up.")
			}
		}

		cfg.extraAssets.each { path ->
			def file = projectFile(path)
			if (!file.exists()) {
				throw new GradleException("[HytalePublisher] GitHub extra asset not found: ${file.absolutePath}")
			}
			files << file
		}

		return files
	}

	private String resolveRepository(GitHubConfig cfg) {
		if (cfg.repository?.trim()) {
			return cfg.repository.trim()
		}

		def remoteUrl
		try {
			remoteUrl = execCapture([
				"git",
				"remote",
				"get-url",
				"origin"
			], rootDirectory.get().asFile).trim()
		} catch (GradleException e) {
			throw new GradleException(
			"[HytalePublisher] github.repository is not set and the 'origin' git remote could not be read. " +
			"Set github.repository = \"owner/repo\" explicitly.",
			e
			)
		}

		def repo = parseGitHubRepo(remoteUrl)
		if (repo == null) {
			throw new GradleException(
			"[HytalePublisher] Could not determine owner/repo from git remote '${remoteUrl}'. " +
			"Set github.repository = \"owner/repo\" explicitly."
			)
		}

		return repo
	}

	private static String parseGitHubRepo(String remoteUrl) {
		def sshMatch   = remoteUrl =~ /git@[^:]+:(.+?)(\.git)?$/
		if (sshMatch.find()) {
			return sshMatch.group(1)
		}

		def httpsMatch = remoteUrl =~ /https?:\/\/[^\/]+\/(.+?)(\.git)?$/
		if (httpsMatch.find()) {
			return httpsMatch.group(1)
		}

		return null
	}

	private String resolveCommitish(GitHubConfig cfg) {
		if (cfg.targetCommitish?.trim()) {
			return cfg.targetCommitish.trim()
		}

		try {
			return execCapture(["git", "rev-parse", "HEAD"], rootDirectory.get().asFile).trim()
		} catch (GradleException e) {
			throw new GradleException(
			"[HytalePublisher] github.targetCommitish is not set and 'git rev-parse HEAD' failed. " +
			"Set github.targetCommitish explicitly, or run this task inside a git checkout.",
			e
			)
		}
	}

	private static String mimeTypeFor(File file) {
		def name = file.name.toLowerCase()
		if (name.endsWith(".jar")) return "application/java-archive"
		if (name.endsWith(".zip")) return "application/zip"
		if (name.endsWith(".json")) return "application/json"
		return "application/octet-stream"
	}
}