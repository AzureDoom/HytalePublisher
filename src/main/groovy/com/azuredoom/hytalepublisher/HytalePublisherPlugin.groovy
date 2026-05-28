package com.azuredoom.hytalepublisher

import org.gradle.api.Plugin
import org.gradle.api.Project

class HytalePublisherPlugin implements Plugin<Project> {
	@Override
	void apply(Project project) {
		def extension = project.extensions.create("hytalePublisher", HytalePublisherExtension, project)

		enforceGitignore(project)

		project.afterEvaluate {
			def keyPropsFile = new File(project.rootProject.projectDir, "key.properties")
			def props = new Properties()
			if (keyPropsFile.exists()) {
				props.load(new FileInputStream(keyPropsFile))
			} else {
				project.logger.info("[HytalePublisher] key.properties not found — credential resolution will rely on environment variables.")
			}

			if (extension.modtale.enabled) {
				project.tasks.register("publishToModtale", ModtalePublishTask) { task ->
					group = "publishing"
					description = "Publishes the built jar and changelog to Modtale.net."
					dependsOn project.tasks.named("build")
					configureCommonPublishInputs(task, project, extension, props)
					modtaleConfig = extension.modtale
				}
			}

			if (extension.curseforge.enabled) {
				project.tasks.register("publishToCurseForge", CurseForgePublishTask) { task ->
					group = "publishing"
					description = "Publishes the built jar and changelog to CurseForge."
					dependsOn project.tasks.named("build")
					configureCommonPublishInputs(task, project, extension, props)
					curseforgeConfig = extension.curseforge
				}
			}

			if (extension.modifold.enabled) {
				project.tasks.register("publishToModifold", ModifoldPublishTask) { task ->
					group = "publishing"
					description = "Publishes the built jar and changelog to Modifold."
					dependsOn project.tasks.named("build")
					configureCommonPublishInputs(task, project, extension, props)
					modifoldConfig = extension.modifold
				}
			}

			if (extension.thunderstore.enabled) {
				project.tasks.register("publishToThunderstore", ThunderstorePublishTask) { task ->
					group = "publishing"
					description = "Builds a Thunderstore package and uploads it to thunderstore.io."
					dependsOn project.tasks.named("build")
					configureCommonPublishInputs(task, project, extension, props)
					thunderstoreConfig = extension.thunderstore
				}
			}

			def enabledTasks = []
			if (extension.modtale.enabled)      enabledTasks << "publishToModtale"
			if (extension.curseforge.enabled)   enabledTasks << "publishToCurseForge"
			if (extension.modifold.enabled)     enabledTasks << "publishToModifold"
			if (extension.thunderstore.enabled) enabledTasks << "publishToThunderstore"

			if (!enabledTasks.isEmpty()) {
				project.tasks.register("publishAll") {
					group = "publishing"
					description = "Publishes to all configured platforms."
					dependsOn enabledTasks
				}
			}
		}
	}

	private static void configureCommonPublishInputs(
			AbstractPublishTask task,
			Project project,
			HytalePublisherExtension extension,
			Properties props
	) {
		task.keyProperties = props
		task.projectName.set(project.name)
		task.projectVersion.set(extension.version.get())
		task.releaseType.set(extension.releaseType.get())
		task.gameVersion.set(extension.gameVersion.get())
		task.changelogFile.set(extension.changelogFile.get())
		task.projectDescription.set(project.description ?: "")
		task.projectDirectory.set(project.layout.projectDirectory)
		task.rootDirectory.set(project.rootProject.layout.projectDirectory)
		task.buildDirectory.set(project.layout.buildDirectory)
		task.versionCacheDirectory.set(new File(project.gradle.gradleUserHomeDir, "caches/hytale-publisher"))
	}

	private static void enforceGitignore(Project project) {
		def rootDir    = project.rootProject.projectDir
		def gitignore  = new File(rootDir, ".gitignore")
		def entry      = "key.properties"

		if (!new File(rootDir, ".git").exists()) return

			if (!gitignore.exists()) {
				gitignore.text = "${entry}\n"
				project.logger.lifecycle("[HytalePublisher] Created .gitignore and added '${entry}'.")
				return
			}

		def lines = gitignore.readLines()
		def alreadyCovered = lines.any { line ->
			def trimmed = line.trim()
			trimmed == entry || trimmed == "/${entry}" || trimmed == "**/key.properties"
		}

		if (!alreadyCovered) {
			def content = gitignore.text
			if (!content.endsWith("\n")) gitignore.append("\n")
			gitignore.append("${entry}\n")
			project.logger.lifecycle("[HytalePublisher] Added '${entry}' to .gitignore.")
		}
	}
}
