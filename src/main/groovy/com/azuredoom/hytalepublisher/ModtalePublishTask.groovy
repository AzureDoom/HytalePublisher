package com.azuredoom.hytalepublisher

import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Publishing uploads files to Modtale and should always execute when requested.")
class ModtalePublishTask extends AbstractPublishTask {

	@TaskAction
	void publish() {
		def ext  = publishExtension
		def cfg  = ext.modtale

		if (!cfg.projectId) throw new GradleException("[HytalePublisher] modtale.projectId must be set.")

		def key  = credentials().require(cfg.apiKeyProp, cfg.apiKeyEnv, "Modtale API key")
		def jar  = resolveJar()
		def log  = readChangelog()
		def curl = curlExe()
		def gameVersion = resolveGameVersion(cfg.patchline)

		def args = [
			curl,
			"-sS",
			"-X",
			"POST",
			"https://api.modtale.net/api/v1/projects/${cfg.projectId}/versions",
			"-H",
			"X-MODTALE-KEY: ${key}",
			"-F",
			"file=@${jar.absolutePath}",
			"-F",
			"versionNumber=${ext.version.get()}",
			"-F",
			"channel=${ext.releaseType.get()}",
			"-F",
			"gameVersions=${gameVersion}",
			"-F",
			"changelog=${log}",
		] as List<String>

		cfg.dependencies.each { dep ->
			def entry = dep.optional
					? "${dep.id}:${dep.version}:optional"
					: "${dep.id}:${dep.version}"
			args += ['-F', "modIds[]=${entry}"]
		}

		exec(args)
		logger.lifecycle("[HytalePublisher] Successfully published to Modtale: ${project.name} ${ext.version.get()}")
	}
}