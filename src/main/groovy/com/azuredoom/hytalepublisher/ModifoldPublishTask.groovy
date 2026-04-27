package com.azuredoom.hytalepublisher

import groovy.json.JsonOutput
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Publishing uploads files to Modifold and should always execute when requested.")
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
			"version_number=${ext.version.get()}",
			"-F",
			"release_channel=${ext.releaseType.get().toLowerCase()}",
			"-F",
			"game_versions=${JsonOutput.toJson(cfg.gameVersions)}",
			"-F",
			"loaders=${JsonOutput.toJson(cfg.loaders)}",
			"-F",
			"changelog=${log}"
		] as List<String>

		if (!cfg.dependencies.isEmpty()) {
			def depsJson = JsonOutput.toJson(cfg.dependencies.collect { dep ->
				def entry = [slug: dep.slug, type: dep.type]
				if (dep.versionId != null && !dep.versionId.isEmpty()) {
					entry.version_id = dep.versionId
				}
				return entry
			})
			args += [
				'-F',
				"dependencies=${depsJson}"
			]
		}

		exec(args)

		logger.lifecycle("[HytalePublisher] Successfully published to Modifold: ${project.name} ${ext.version.get()}")
	}
}