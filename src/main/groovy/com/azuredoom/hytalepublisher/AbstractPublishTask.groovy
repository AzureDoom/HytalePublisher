package com.azuredoom.hytalepublisher

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.internal.os.OperatingSystem
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Publishing uploads files to external services and should always execute when requested.")
abstract class AbstractPublishTask extends DefaultTask {

	@Internal
	Properties keyProperties = new Properties()

	@Input
	abstract Property<String> getProjectName()

	@Input
	abstract Property<String> getProjectVersion()

	@Input
	abstract Property<String> getReleaseType()

	@Input
	abstract Property<String> getGameVersion()

	@Input
	abstract Property<String> getChangelogFile()

	@Input
	@Optional
	abstract Property<String> getProjectDescription()

	@Internal
	abstract DirectoryProperty getProjectDirectory()

	@Internal
	abstract DirectoryProperty getRootDirectory()

	@Internal
	abstract DirectoryProperty getBuildDirectory()

	@Internal
	abstract DirectoryProperty getVersionCacheDirectory()

	protected static String curlExe() {
		OperatingSystem.current().isWindows() ? "C:/Windows/System32/curl.exe" : "curl"
	}

	protected File resolveJar() {
		def jarFile = buildDirectory
				.file("libs/${projectName.get()}-${projectVersion.get()}.jar")
				.get().asFile

		if (!jarFile.exists()) {
			throw new GradleException("Jar not found: ${jarFile.absolutePath}")
		}

		return jarFile
	}

	protected String readChangelog() {
		return resolveChangelogFile().text
	}

	protected CredentialResolver credentials() {
		return new CredentialResolver(keyProperties)
	}

	protected String resolveGameVersion(String patchline) {
		String configured = gameVersion.get()
		return HytaleVersionResolver.resolve(versionCacheDirectory.get().asFile, logger, configured, patchline)
	}

	protected File projectFile(String path) {
		File file = new File(path)
		return file.absolute ? file : new File(projectDirectory.get().asFile, path)
	}

	protected File rootFile(String path) {
		File file = new File(path)
		return file.absolute ? file : new File(rootDirectory.get().asFile, path)
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

	protected static String execCapture(List<String> args) {
		def command = args.collect { it.toString() }

		def process = new ProcessBuilder(command)
				.redirectErrorStream(true)
				.start()

		def output = new StringBuilder()

		process.inputStream.withReader { reader ->
			reader.eachLine { line ->
				output.append(line).append('\n')
			}
		}

		def exitCode = process.waitFor()

		if (exitCode != 0) {
			throw new GradleException(
			"[HytalePublisher] Upload failed with exit code ${exitCode}.\n\n" +
			output.toString()
			)
		}

		return output.toString()
	}

	protected File resolveChangelogFile() {
		File configured = rootFile(changelogFile.get())

		if (configured.exists()) {
			return configured
		}

		File matching = findCaseInsensitive(configured)
		if (matching != null) {
			return matching
		}

		if (configured.parentFile != null && !configured.parentFile.exists()) {
			configured.parentFile.mkdirs()
		}

		configured.text = "# Changelog${System.lineSeparator()}${System.lineSeparator()}"
		logger.lifecycle("[HytalePublisher] Created missing changelog file: ${configured.absolutePath}")
		return configured
	}

	protected static File findCaseInsensitive(File file) {
		File parent = file.parentFile
		if (parent == null || !parent.exists() || !parent.isDirectory()) {
			return null
		}

		return parent.listFiles()?.find { candidate ->
			candidate.name.equalsIgnoreCase(file.name)
		}
	}
}