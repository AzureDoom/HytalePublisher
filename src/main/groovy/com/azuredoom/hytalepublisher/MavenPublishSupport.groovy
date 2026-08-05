package com.azuredoom.hytalepublisher

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin

@SuppressWarnings("unused")
class MavenPublishSupport {

	static void configure(Project project, HytalePublisherExtension extension, Properties keyProperties) {
		def cfg = extension.maven

		if (!cfg.url?.trim()) {
			throw new GradleException("[HytalePublisher] maven.url must be set when maven.enabled = true.")
		}

		project.pluginManager.apply(MavenPublishPlugin)

		def resolvedGroupId    = cfg.groupId?.trim()    ? cfg.groupId    : project.group.toString()
		def resolvedArtifactId = cfg.artifactId?.trim() ? cfg.artifactId : project.name
		def resolvedVersion    = cfg.version?.trim()    ? cfg.version    : extension.version.get()

		def publishing = project.extensions.getByType(PublishingExtension)

		publishing.publications.create(cfg.publicationName, MavenPublication) { MavenPublication publication ->
			publication.groupId    = resolvedGroupId
			publication.artifactId = resolvedArtifactId
			publication.version    = resolvedVersion

			if (cfg.includeJar) {
				def jarTask = project.tasks.findByName(cfg.jarTaskName)
				if (jarTask == null) {
					throw new GradleException("[HytalePublisher] maven.includeJar is true but task '${cfg.jarTaskName}' was not found.")
				}
				publication.artifact(jarTask)
			}

			if (cfg.includeSourcesJar) {
				def sourcesTask = project.tasks.findByName(cfg.sourcesJarTaskName)
				if (sourcesTask != null) {
					publication.artifact(sourcesTask)
				} else {
					project.logger.warn("[HytalePublisher] maven.includeSourcesJar is true but task '${cfg.sourcesJarTaskName}' was not found.")
				}
			}

			if (cfg.includeJavadocJar) {
				def javadocTask = project.tasks.findByName(cfg.javadocJarTaskName)
				if (javadocTask != null) {
					publication.artifact(javadocTask)
				} else {
					project.logger.warn("[HytalePublisher] maven.includeJavadocJar is true but task '${cfg.javadocJarTaskName}' was not found.")
				}
			}

			cfg.extraArtifactPaths.each { path ->
				def file = project.file(path)
				if (!file.exists()) {
					throw new GradleException("[HytalePublisher] Maven extra artifact not found: ${file.absolutePath}")
				}
				publication.artifact(file)
			}

			publication.pom { pom ->
				if (cfg.pomName?.trim())        pom.name.set(cfg.pomName)
				if (cfg.pomDescription?.trim()) pom.description.set(cfg.pomDescription)
				if (cfg.pomUrl?.trim())         pom.url.set(cfg.pomUrl)
				if (cfg.pomAction != null)      cfg.pomAction.execute(pom)
			}
		}

		publishing.repositories.maven { repo ->
			repo.name = cfg.repositoryName
			repo.url  = project.uri(resolveUrl(cfg, resolvedVersion))
			repo.allowInsecureProtocol = cfg.allowInsecureProtocol

			def resolver = new CredentialResolver(keyProperties, System.getenv(), project)
			def username = resolver.optional(cfg.usernameProp, cfg.usernameEnv)
			def password = resolver.optional(cfg.passwordProp, cfg.passwordEnv)

			if (username && password) {
				repo.credentials { credentials ->
					credentials.username = username
					credentials.password = password
				}
			} else {
				project.logger.info("[HytalePublisher] No maven credentials configured for '${cfg.repositoryName}' — publishing will rely on the repository allowing unauthenticated access.")
			}
		}

		def gradlePublishTaskName = "publish${cfg.publicationName.capitalize()}PublicationTo${cfg.repositoryName.capitalize()}Repository"

		project.tasks.register("publishToMaven") { task ->
			group = "publishing"
			description = "Publishes the '${cfg.publicationName}' publication to the configured custom Maven repository."
			dependsOn project.tasks.named("build")
			dependsOn gradlePublishTaskName
		}
	}

	private static String resolveUrl(MavenConfig cfg, String resolvedVersion) {
		if (cfg.snapshotUrl?.trim() && resolvedVersion.endsWith("-SNAPSHOT")) {
			return cfg.snapshotUrl
		}
		return cfg.url
	}
}