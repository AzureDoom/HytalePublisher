package com.azuredoom.hytalepublisher

import org.gradle.api.GradleException

@SuppressWarnings("unused")
class CredentialResolver {

	private final Properties fileProps
	private final Map<String, String> env

	CredentialResolver(Properties fileProps, Map<String, String> env = System.getenv()) {
		this.fileProps = fileProps ?: new Properties()
		this.env       = env
	}

	String require(String propKey, String envKey, String label) {
		def value = env[envKey] ?: fileProps.getProperty(propKey)
		if (!value) {
			throw new GradleException(
			"[HytalePublisher] Missing credential for ${label}.\n" +
			"  Set environment variable '${envKey}'\n" +
			"  or add '${propKey}' to key.properties."
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
}
