package com.azuredoom.hytalepublisher

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@DisableCachingByDefault(because = "Publishing uploads files to Thunderstore and should always execute when requested.")
abstract class ThunderstorePublishTask extends AbstractPublishTask {

	private static final long DEFAULT_PART_SIZE = 1024L * 1024L * 50L

	@Internal
	ThunderstoreConfig thunderstoreConfig

	@TaskAction
	void publish() {
		def cfg = thunderstoreConfig

		validate(cfg)

		def key = credentials().require(cfg.apiKeyProp, cfg.apiKeyEnv, "Thunderstore API token")

		def packageVersion = resolvePackageVersion()
		def packageName    = resolvePackageName(cfg)

		def stagingDir = buildDirectory
				.dir("hytale-publisher/thunderstore")
				.get().asFile
		deleteDirectory(stagingDir)
		stagingDir.mkdirs()

		writeManifest(stagingDir, cfg, packageName, packageVersion)
		copyRequiredFiles(stagingDir, cfg)
		copyContentBundles(stagingDir, cfg)
		copyExtras(stagingDir, cfg)

		def zipFile = buildZip(stagingDir, packageName, packageVersion)
		logger.lifecycle("[HytalePublisher] Built Thunderstore package: ${zipFile.name} (${zipFile.length()} bytes)")

		def uploadUuid = uploadFile(cfg, key, zipFile)
		submitPackage(cfg, key, uploadUuid)

		logger.lifecycle("[HytalePublisher] Successfully published to Thunderstore: ${packageName} ${packageVersion}")
	}

	private void validate(ThunderstoreConfig cfg) {
		def errors = []
		if (!cfg.namespace) {
			errors << "thunderstore.namespace must be set (your Thunderstore team name)."
		}
		def communities = []
		if (cfg.community) communities << cfg.community
		if (cfg.communities) communities.addAll(cfg.communities)
		if (communities.unique().isEmpty()) {
			errors << "thunderstore.community must be set (e.g. \"hytale\")."
		}
		def name = cfg.packageName ?: projectName.get()
		if (name && (name.contains(' ') || !(name ==~ /^[A-Za-z0-9_]+$/))) {
			errors << "thunderstore.packageName must be alphanumeric + underscores, no spaces. Got: '${name}'"
		}
		if (cfg.description && cfg.description.length() > 250) {
			errors << "thunderstore.description must be 250 characters or fewer (currently ${cfg.description.length()})."
		}
		if (errors) {
			throw new GradleException(
			"[HytalePublisher] Thunderstore configuration errors:\n  - " + errors.join("\n  - ")
			)
		}
	}

	private String resolvePackageVersion() {
		def v = projectVersion.get()
		if (!v || v == "unspecified") {
			throw new GradleException(
			"[HytalePublisher] No version configured. Set project.version or hytalePublisher.version."
			)
		}
		def m = v =~ /^(\d+\.\d+\.\d+)/
		if (!m) {
			throw new GradleException(
			"[HytalePublisher] Thunderstore requires a SemVer version (Major.Minor.Patch). Got: '${v}'"
			)
		}
		return m.group(1)
	}

	private String resolvePackageName(ThunderstoreConfig cfg) {
		def n = cfg.packageName ?: projectName.get()
		return n.replaceAll(/\s+/, "_")
	}

	private void writeManifest(File staging, ThunderstoreConfig cfg, String packageName, String packageVersion) {
		def manifest = [
			name           : packageName,
			version_number : packageVersion,
			website_url    : cfg.websiteUrl ?: "",
			description    : cfg.description ?: projectDescription.orNull ?: "",
			dependencies   : cfg.dependencies ?: []
		]
		new File(staging, "manifest.json").text = JsonOutput.prettyPrint(JsonOutput.toJson(manifest))
	}

	private void copyRequiredFiles(File staging, ThunderstoreConfig cfg) {
		def iconSource = cfg.iconFile
				? projectFile(cfg.iconFile)
				: projectFile("icon.png")
		if (!iconSource.exists()) {
			throw new GradleException(
			"[HytalePublisher] Required icon.png not found at: ${iconSource.absolutePath}. " +
			"Provide a 256x256 PNG named icon.png in the project root, or set thunderstore.iconFile."
			)
		}
		Files.copy(iconSource.toPath(),
				new File(staging, "icon.png").toPath(),
				StandardCopyOption.REPLACE_EXISTING)

		def readmeSource = cfg.readmeFile
				? projectFile(cfg.readmeFile)
				: projectFile("README.md")
		if (!readmeSource.exists()) {
			throw new GradleException(
			"[HytalePublisher] Required README.md not found at: ${readmeSource.absolutePath}. " +
			"Provide a README.md in the project root, or set thunderstore.readmeFile."
			)
		}
		Files.copy(readmeSource.toPath(),
				new File(staging, "README.md").toPath(),
				StandardCopyOption.REPLACE_EXISTING)

		def changelogSource = rootFile(changelogFile.get())
		if (changelogSource.exists()) {
			Files.copy(changelogSource.toPath(),
					new File(staging, "CHANGELOG.md").toPath(),
					StandardCopyOption.REPLACE_EXISTING)
		} else {
			logger.info("[HytalePublisher] Configured changelog not found at ${changelogSource.absolutePath}; skipping CHANGELOG.md.")
		}
	}

	private void copyContentBundles(File staging, ThunderstoreConfig cfg) {
		cfg.contentBundles.each { folder, paths ->
			def folderDir = new File(staging, folder)
			folderDir.mkdirs()
			paths.each { p ->
				def source = projectFile(p)
				if (!source.exists()) {
					throw new GradleException(
					"[HytalePublisher] Thunderstore content path not found: ${source.absolutePath}"
					)
				}
				if (source.isFile()) {
					Files.copy(source.toPath(),
							new File(folderDir, source.name).toPath(),
							StandardCopyOption.REPLACE_EXISTING)
				} else {
					copyDirectory(source, new File(folderDir, source.name))
				}
			}
		}

		def hasContent = !cfg.contentBundles.isEmpty() || !cfg.extraIncludes.isEmpty()
		if (!hasContent) {
			def jar = resolveJar()
			def modsDir = new File(staging, "mods")
			modsDir.mkdirs()
			Files.copy(jar.toPath(),
					new File(modsDir, jar.name).toPath(),
					StandardCopyOption.REPLACE_EXISTING)
			logger.lifecycle("[HytalePublisher] No content bundles configured; using built jar: mods/${jar.name}")
		}
	}

	private void copyExtras(File staging, ThunderstoreConfig cfg) {
		cfg.extraIncludes.each { p ->
			def source = projectFile(p)
			if (!source.exists()) {
				throw new GradleException(
				"[HytalePublisher] Thunderstore extra include not found: ${source.absolutePath}"
				)
			}
			if (source.isFile()) {
				Files.copy(source.toPath(),
						new File(staging, source.name).toPath(),
						StandardCopyOption.REPLACE_EXISTING)
			} else {
				copyDirectory(source, new File(staging, source.name))
			}
		}
	}

	private static File buildZip(File staging, String packageName, String packageVersion) {
		def zipFile = new File(staging.parentFile, "${packageName}-${packageVersion}.zip")
		zipFile.delete()
		new ZipOutputStream(new FileOutputStream(zipFile)).withCloseable { zos ->
			Path stagingPath = staging.toPath()
			staging.eachFileRecurse { f ->
				if (f.isDirectory()) return
					def rel = stagingPath.relativize(f.toPath())
							.toString()
							.replace(File.separatorChar, (char) '/')
				zos.putNextEntry(new ZipEntry(rel))
				f.withInputStream { ins -> zos << ins }
				zos.closeEntry()
			}
		}
		return zipFile
	}

	private String uploadFile(ThunderstoreConfig cfg, String token, File zipFile) {
		long size = zipFile.length()

		def initBody = JsonOutput.toJson([
			filename       : zipFile.name,
			file_size_bytes: size
		])
		def initResp = postJson(cfg, "${cfg.repository}/api/experimental/usermedia/initiate-upload/",
				token, initBody, "initiate-upload")
		def initJson = new JsonSlurper().parseText(initResp)

		def uuid = (initJson.user_media?.uuid ?: initJson.uuid) as String
		def uploadUrls = (initJson.upload_urls ?: []) as List

		if (!uuid) {
			throw new GradleException(
			"[HytalePublisher] Thunderstore initiate-upload returned no UUID. Response: ${initResp}"
			)
		}
		if (uploadUrls.isEmpty()) {
			throw new GradleException(
			"[HytalePublisher] Thunderstore initiate-upload returned no upload URLs. Response: ${initResp}"
			)
		}

		uploadUrls = uploadUrls.toSorted { a, b ->
			((a.part_number ?: 0) as int) <=> ((b.part_number ?: 0) as int)
		}

		List<Map> finishParts = []
		zipFile.withInputStream { ins ->
			uploadUrls.eachWithIndex { entry, int idx ->
				int partNumber = (entry.part_number ?: (idx + 1)) as int
				long partSize  = (entry.length ?: entry.file_size_bytes ?: DEFAULT_PART_SIZE) as long
				byte[] buf     = readPart(ins, partSize)
				logger.info("[HytalePublisher] Uploading part ${partNumber} (${buf.length} bytes)")
				String etag    = putToS3(entry.url as String, buf)
				finishParts << [ETag: etag, PartNumber: partNumber]
			}
		}

		def finishBody = JsonOutput.toJson([parts: finishParts])
		postJson(cfg, "${cfg.repository}/api/experimental/usermedia/${uuid}/finish-upload/",
				token, finishBody, "finish-upload")

		return uuid
	}

	private static void submitPackage(ThunderstoreConfig cfg, String token, String uploadUuid) {
		def communities = []
		if (cfg.community) communities << cfg.community
		if (cfg.communities) communities.addAll(cfg.communities)
		communities = communities.unique()

		def communityCategories = [:]
		communities.each { c ->
			communityCategories[c] = cfg.categories ?: []
		}

		def submitBody = [
			upload_uuid          : uploadUuid,
			author_name          : cfg.namespace,
			communities          : communities,
			community_categories : communityCategories,
			has_nsfw_content     : cfg.hasNsfwContent
		]

		postJson(cfg, "${cfg.repository}/api/experimental/submission/submit/",
				token, JsonOutput.toJson(submitBody), "submission/submit")
	}

	private static String postJson(ThunderstoreConfig cfg, String url, String token, String body, String label) {
		def conn = (HttpURLConnection) URI.create(url).toURL().openConnection()
		conn.requestMethod = "POST"
		conn.doOutput = true
		conn.setRequestProperty("Authorization", "Bearer ${token}")
		conn.setRequestProperty("Content-Type", "application/json")
		conn.setRequestProperty("Accept", "application/json")
		conn.connectTimeout = 30_000
		conn.readTimeout    = 120_000
		conn.outputStream.withWriter("UTF-8") { it.write(body) }

		int code = conn.responseCode
		def stream = (code >= 200 && code < 300) ? conn.inputStream : conn.errorStream
		def text = stream ? stream.getText("UTF-8") : ""
		if (code < 200 || code >= 300) {
			throw new GradleException(
			"[HytalePublisher] Thunderstore ${label} failed: HTTP ${code}\n${text}"
			)
		}
		return text
	}

	static String putToS3(String url, byte[] data) {
		def conn = (HttpURLConnection) URI.create(url).toURL().openConnection()
		conn.requestMethod = "PUT"
		conn.doOutput = true
		conn.setFixedLengthStreamingMode(data.length)
		conn.setRequestProperty("Content-Type", "application/octet-stream")
		conn.connectTimeout = 30_000
		conn.readTimeout    = 600_000
		conn.outputStream.withCloseable { it.write(data) }

		int code = conn.responseCode
		if (code < 200 || code >= 300) {
			def err = conn.errorStream ? conn.errorStream.getText("UTF-8") : ""
			throw new GradleException(
			"[HytalePublisher] S3 part upload failed: HTTP ${code}\n${err}"
			)
		}
		def etag = conn.getHeaderField("ETag")
		if (!etag) {
			throw new GradleException("[HytalePublisher] S3 PUT did not return an ETag header.")
		}
		return etag
	}

	static byte[] readPart(InputStream ins, long n) {
		def byteArryOutputStream = new ByteArrayOutputStream()
		byte[] buf = new byte[8192]
		long remaining = n
		while (remaining > 0) {
			int want = (int) Math.min((long) buf.length, remaining)
			int read = ins.read(buf, 0, want)
			if (read < 0) break
				byteArryOutputStream.write(buf, 0, read)
			remaining -= read
		}
		return byteArryOutputStream.toByteArray()
	}
}
	private static void copyDirectory(File source, File target) {
		Path sourcePath = source.toPath()
		Path targetPath = target.toPath()
		Files.walk(sourcePath).withCloseable { stream ->
			stream.each { Path path ->
				Path relative = sourcePath.relativize(path)
				Path destination = targetPath.resolve(relative)
				if (Files.isDirectory(path)) {
					Files.createDirectories(destination)
				} else {
					Files.createDirectories(destination.parent)
					Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING)
				}
			}
		}
	}

	private static void deleteDirectory(File directory) {
		if (!directory.exists()) {
			return
		}
		Files.walk(directory.toPath())
				.sorted(Comparator.reverseOrder())
				.forEach { Path path -> Files.delete(path) }
	}
}
