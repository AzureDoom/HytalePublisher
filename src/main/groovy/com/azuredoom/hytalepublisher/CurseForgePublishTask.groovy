package com.azuredoom.hytalepublisher

import groovy.json.JsonOutput
import org.gradle.api.GradleException
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Publishing uploads files to CurseForge and should always execute when requested.")
abstract class CurseForgePublishTask extends AbstractPublishTask {

	@Internal
	CurseForgeConfig curseforgeConfig

	@TaskAction
	void publish() {
		def cfg = curseforgeConfig

		if (!cfg.projectId) throw new GradleException("[HytalePublisher] curseforge.projectId must be set.")

		def key  = credentials().require(cfg.apiKeyProp, cfg.apiKeyEnv, "CurseForge API token")
		def jar  = resolveJar()
		def log  = readChangelog()
		def curl = curlExe()

		def deps = cfg.dependencies.collect { dep ->
			[slug: dep.slug, type: dep.type]
		}

		def metadataMap = [
			changelog    : log,
			changelogType: "markdown",
			displayName  : "${projectName.get()} ${projectVersion.get()}",
			gameVersions : cfg.gameVersionIds,
			releaseType  : releaseType.get().toLowerCase()
		]

		if (!deps.isEmpty()) {
			metadataMap.relations = [projects: deps]
		}

		def metadata = JsonOutput.toJson(metadataMap)
		def metadataFile = File.createTempFile("hytalepublisher-curseforge-metadata-", ".json")
		metadataFile.text = metadata

		exec([
			curl,
			"-f",
			"-sS",
			"-X",
			"POST",
			"https://legacy.curseforge.com/api/projects/${cfg.projectId}/upload-file",
			"-H",
			"X-Api-Token: ${key}",
			"-F",
			"metadata=<${metadataFile.absolutePath};type=application/json",
			"-F",
			"file=@${jar.absolutePath}"
		])

		logger.lifecycle("[HytalePublisher] Successfully published to CurseForge: ${projectName.get()} ${projectVersion.get()}")
	}
}
