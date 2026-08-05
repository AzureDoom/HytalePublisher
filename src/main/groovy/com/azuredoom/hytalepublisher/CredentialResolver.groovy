package com.azuredoom.hytalepublisher

import org.gradle.api.GradleException
import org.gradle.api.Project

@SuppressWarnings("unused")
class CredentialResolver {

	private final Properties fileProps
	private final Map<String, String> env
	private final Project project

	CredentialResolver(Properties fileProps, Map<String, String> env = System.getenv(), Project project = null) {
		this.fileProps = fileProps ?: new Properties()
		this.env       = env
		this.project   = project
	}

	String require(String propKey, String envKey, String label) {
		def value = resolve(propKey, envKey)
		if (!value) {
			throw new GradleException(
			"[HytalePublisher] Missing credential for ${label}.\n" +
			"  Set environment variable '${envKey}'\n" +
			"  or add '${propKey}' to key.properties\n" +
			"  or add '${propKey}' to a gradle.properties file (project directory or ~/.gradle)."
			)
		}
		return value
	}

	String require(String propKey, String label) {
		def envKey = propKey
				.replaceAll(/([a-z])([A-Z])/, '$1_$2')
				.toUpperCase()
		return require(propKey, envKey, label)
	}

	String optional(String propKey, String envKey) {
		return resolve(propKey, envKey)
	}

	private String resolve(String propKey, String envKey) {
		def value = env[envKey]
		if (value) return value

		value = fileProps.getProperty(propKey)
		if (value) return value

		if (project != null && project.hasProperty(propKey)) {
			def propValue = project.property(propKey)
			return propValue != null ? propValue.toString() : null
		}

		return null
	}
}