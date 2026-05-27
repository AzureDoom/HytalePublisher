package com.azuredoom.hytalepublisher

import groovy.json.JsonOutput
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Publishing uploads files to CurseForge and should always execute when requested.")
class CurseForgePublishTask extends AbstractPublishTask {

	@TaskAction
	void publish() {
		def ext = publishExtension
		def cfg = ext.curseforge

		if (!cfg.projectId) throw new GradleException("[HytalePublisher] curseforge.projectId must be set.")

		def key  = credentials().require(cfg.apiKeyProp, cfg.apiKeyEnv, "CurseForge API token")
		def jar  = resolveJar()
		def log  = readChangelog()
		def curl = curlExe()

		def deps = cfg.dependencies.collect { dep ->
			[slug: dep.id, type: dep.optional ? "optionalDependency" : "requiredDependency"]
		}

		def metadataMap = [
			changelog    : log,
			changelogType: "markdown",
			displayName  : "${project.name} ${ext.version.get()}",
			gameVersions : cfg.gameVersionIds,
			releaseType  : ext.releaseType.get().toLowerCase()
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

		logger.lifecycle("[HytalePublisher] Successfully published to CurseForge: ${project.name} ${ext.version.get()}")
	}
}
