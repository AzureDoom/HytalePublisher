package com.azuredoom.hytalepublisher

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Publishing uploads files to Modifold and should always execute when requested.")
abstract class ModifoldPublishTask extends AbstractPublishTask {

	@Internal
	ModifoldConfig modifoldConfig

	private final Map<String, String> projectSlugCache = [:]

	@TaskAction
	void publish() {
		def cfg = modifoldConfig

		if (!cfg.projectId) {
			throw new GradleException("[HytalePublisher] modifold.projectId must be set.")
		}

		def key = credentials().require(cfg.apiKeyProp, cfg.apiKeyEnv, "Modifold API key")
		def jar = resolveJar()
		def log = readChangelog()
		def curl = curlExe()

		def gameVersions = resolveModifoldGameVersions(cfg)

		def depsJson = JsonOutput.toJson(cfg.dependencies.collect { dep ->
			def slug = this.resolveModifoldProjectSlug(curl, key, dep.project)

			def entry = [
				slug: slug,
				type: dep.type
			]

			if (dep.versionId != null && !dep.versionId.isEmpty()) {
				entry.version_id = dep.versionId
			}

			return entry
		})

		def args = [
			curl,
			"-f",
			"-sS",
			"-X",
			"POST",
			"https://api.modifold.com/projects/${cfg.projectId}/versions",
			"-H",
			"Authorization: Bearer ${key}",
			"-F",
			"file=@${jar.absolutePath}",
			"-F",
			"version_number=${projectVersion.get()}",
			"-F",
			"changelog=${log}",
			"-F",
			"release_channel=${releaseType.get().toLowerCase()}",
			"-F",
			"game_versions=${JsonOutput.toJson(gameVersions)}",
			"-F",
			"loaders=${JsonOutput.toJson(cfg.loaders)}",
			"-F",
			"dependencies=${depsJson}"
		] as List

		exec(args)

		logger.lifecycle("[HytalePublisher] Successfully published to Modifold: ${projectName.get()} ${projectVersion.get()}")
	}

	private List<String> resolveModifoldGameVersions(ModifoldConfig cfg) {
		def configured = cfg.gameVersions.collect { it.toString().trim() }.findAll { !it.isEmpty() }

		if (configured.isEmpty()) {
			def fallback = gameVersion.get()?.toString()?.trim()

			if (!fallback) {
				throw new GradleException(
				"[HytalePublisher] Modifold requires at least one game version. " +
				"Set modifold.gameVersions = [\"0.5.0-pre.9.1\"] or define hytale_version."
				)
			}

			configured = [
				HytaleVersionResolver.resolve(versionCacheDirectory.get().asFile, logger, fallback, cfg.patchline)
			]
		}

		if (configured.any { it.equalsIgnoreCase("Early Access") }) {
			throw new GradleException(
			"[HytalePublisher] Modifold no longer supports the 'Early Access' game version category. " +
			"Use exact Hytale game version names, e.g. modifold.gameVersions = [\"0.5.0-pre.9.1\"]."
			)
		}

		return configured
	}

	String resolveModifoldProjectSlug(
			String curl,
			String apiKey,
			String projectIdentifier
	) {
		if (projectIdentifier == null || projectIdentifier.trim().isEmpty()) {
			throw new GradleException(
			"[HytalePublisher] Modifold dependency project cannot be empty."
			)
		}

		def identifier = projectIdentifier.trim()

		def cached = projectSlugCache[identifier]
		if (cached != null) {
			return cached
		}

		def output = new ByteArrayOutputStream()
		def errorOutput = new ByteArrayOutputStream()

		def process = new ProcessBuilder(
				curl,
				"-f",
				"-sS",
				"-X",
				"GET",
				"https://api.modifold.com/projects/${identifier}",
				"-H",
				"Authorization: Bearer ${apiKey}"
				).start()

		process.consumeProcessOutput(output, errorOutput)

		def exitCode = process.waitFor()

		if (exitCode != 0) {
			throw new GradleException(
			"[HytalePublisher] Failed to resolve Modifold dependency '${identifier}': " +
			errorOutput.toString("UTF-8").trim()
			)
		}

		def responseText = output.toString("UTF-8").trim()

		if (!responseText) {
			throw new GradleException(
			"[HytalePublisher] Modifold returned an empty response while resolving dependency '${identifier}'."
			)
		}

		def response

		try {
			response = new JsonSlurper().parseText(responseText)
		} catch (Exception e) {
			throw new GradleException(
			"[HytalePublisher] Failed to parse Modifold project response for '${identifier}'.",
			e
			)
		}

		def slug = response.slug?.toString()?.trim()

		if (!slug) {
			throw new GradleException(
			"[HytalePublisher] Modifold project '${identifier}' did not contain a slug."
			)
		}

		projectSlugCache[identifier] = slug

		logger.info(
				"[HytalePublisher] Resolved Modifold dependency '{}' -> '{}'",
				identifier,
				slug
				)

		return slug
	}
}
