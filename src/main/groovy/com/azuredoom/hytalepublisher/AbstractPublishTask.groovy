package com.azuredoom.hytalepublisher

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Internal
import org.gradle.internal.os.OperatingSystem
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Publishing uploads files to external services and should always execute when requested.")
abstract class AbstractPublishTask extends DefaultTask {

	@Internal
	Properties keyProperties = new Properties()

	@Internal
	HytalePublisherExtension publishExtension

	protected static String curlExe() {
		OperatingSystem.current().isWindows() ? "C:/Windows/System32/curl.exe" : "curl"
	}

	protected File resolveJar() {
		def ext = publishExtension
		def jarFile = project.layout.buildDirectory
				.file("libs/${project.name}-${ext.version.get()}.jar")
				.get().asFile

		if (!jarFile.exists()) {
			throw new GradleException("Jar not found: ${jarFile.absolutePath}")
		}

		return jarFile
	}

	protected String readChangelog() {
		def ext = publishExtension
		return project.rootProject.file(ext.changelogFile.get()).text
	}

	protected CredentialResolver credentials() {
		return new CredentialResolver(keyProperties)
	}

	protected static void exec(List<String> args) {
		def command = args.collect { it.toString() }

		def process = new ProcessBuilder(command)
				.redirectErrorStream(true)
				.start()

		def output = new StringBuilder()

		process.inputStream.withReader { reader ->
			reader.eachLine { line ->
				output.append(line).append(System.lineSeparator())
			}
		}

		def exitCode = process.waitFor()

		if (exitCode != 0) {
			throw new GradleException(
			"[HytalePublisher] Upload failed with exit code ${exitCode}.\n\n" +
			output.toString()
			)
		}

		if (output.length() > 0) {
			println output.toString()
		}
	}
}