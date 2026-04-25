package com.azuredoom.hytalepublisher

import groovy.json.JsonOutput
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction

class ModifoldPublishTask extends AbstractPublishTask {

	@TaskAction
	void publish() {
		def ext = publishExtension
		def cfg = ext.modifold

		if (!cfg.projectId) throw new GradleException("[HytalePublisher] modifold.projectId must be set.")

		def key  = credentials().require(cfg.apiKeyProp, cfg.apiKeyEnv, "Modifold API key")
		def jar  = resolveJar()
		def log  = readChangelog()
		def curl = curlExe()

		exec([
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
			"version_number=${ext.version.get()}",
			"-F",
			"release_channel=${ext.releaseType.get().toLowerCase()}",
			"-F",
			"game_versions=${JsonOutput.toJson(cfg.gameVersions)}",
			"-F",
			"loaders=${JsonOutput.toJson(cfg.loaders)}",
			"-F",
			"changelog=${log}"
		])

		logger.lifecycle("[HytalePublisher] Successfully published to Modifold: ${project.name} ${ext.version.get()}")
	}
}
