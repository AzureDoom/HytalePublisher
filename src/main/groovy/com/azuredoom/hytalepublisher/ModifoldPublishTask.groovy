package com.azuredoom.hytalepublisher

import groovy.json.JsonOutput
import org.gradle.api.GradleException
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Publishing uploads files to Modifold and should always execute when requested.")
abstract class ModifoldPublishTask extends AbstractPublishTask {

	@Internal
	ModifoldConfig modifoldConfig

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
			def entry = [slug: dep.slug, type: dep.type]
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
}
