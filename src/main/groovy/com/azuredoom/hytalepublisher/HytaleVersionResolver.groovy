package com.azuredoom.hytalepublisher

import groovy.xml.XmlSlurper
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger

final class HytaleVersionResolver {

	static final String SERVER_GROUP = 'com.hypixel.hytale'
	static final String SERVER_MODULE = 'Server'
	static final String RELEASE_REPO_URL = 'https://maven.hytale.com/release'
	static final String PRE_RELEASE_REPO_URL = 'https://maven.hytale.com/pre-release'

	private static final long CACHE_TTL_MILLIS = 10L * 60L * 1000L

	private HytaleVersionResolver() {}

	static boolean isDynamicSelector(String value) {
		if (value == null || value.isEmpty()) {
			return false
		}
		if (value.endsWith('+')) {
			return true
		}
		if (value.startsWith('latest.')) {
			return true
		}
		if (value.startsWith('[') || value.startsWith('(')) {
			return true
		}
		return false
	}

	static String resolve(File cacheDir, Logger logger, String selector, String patchline) {
		if (!isDynamicSelector(selector)) {
			return selector
		}

		if (selector.startsWith('[') || selector.startsWith('(')) {
			throw new GradleException(
			"[HytalePublisher] Range version selectors like '${selector}' are not supported. " +
			"Use a prefix selector such as '2026.+' or set a concrete version."
			)
		}

		String repoUrl = repoUrlForPatchline(patchline)
		List<String> versions = fetchVersions(cacheDir, logger, repoUrl, patchline)
		String resolved = pickVersion(selector, versions)

		if (resolved == null) {
			throw new GradleException(
			"[HytalePublisher] No versions in the ${patchline} patchline matched selector '${selector}'. " +
			"Available versions: ${versions.takeRight(5).join(', ')}${versions.size() > 5 ? ' (and more)' : ''}."
			)
		}

		logger.lifecycle(
				"[HytalePublisher] Resolved gameVersion '{}' to '{}' against patchline '{}'.",
				selector, resolved, patchline)
		return resolved
	}

	private static String repoUrlForPatchline(String patchline) {
		String normalized = (patchline ?: 'release').trim().toLowerCase()
		if (normalized == 'pre-release' || normalized == 'prerelease') {
			return PRE_RELEASE_REPO_URL
		}
		return RELEASE_REPO_URL
	}

	private static List<String> fetchVersions(File cacheDir, Logger logger, String repoUrl, String patchline) {
		cacheDir.mkdirs()

		String cacheKey = (patchline ?: 'release').trim().toLowerCase().replaceAll(/[^a-z0-9-]/, '_')
		File cacheFile = new File(cacheDir, "maven-metadata-${cacheKey}.xml")

		long now = System.currentTimeMillis()
		boolean cacheFresh = cacheFile.exists() &&
				(now - cacheFile.lastModified()) < CACHE_TTL_MILLIS

		String xml
		if (cacheFresh) {
			xml = cacheFile.text
		} else {
			String metadataUrl = "${repoUrl}/${SERVER_GROUP.replace('.', '/')}/${SERVER_MODULE}/maven-metadata.xml"
			try {
				xml = downloadText(metadataUrl)
				cacheFile.text = xml
			} catch (Exception e) {
				if (cacheFile.exists()) {
					logger.warn(
							"[HytalePublisher] Failed to refresh Maven metadata from {}, using cached copy. ({})",
							metadataUrl, e.message)
					xml = cacheFile.text
				} else {
					throw new GradleException(
					"[HytalePublisher] Could not fetch Hytale server version metadata from ${metadataUrl}. " +
					"Check your internet connection and that the patchline is correct.",
					e)
				}
			}
		}

		try {
			def metadata = new XmlSlurper().parseText(xml)
			return metadata.versioning.versions.version*.text() as List<String>
		} catch (Exception e) {
			throw new GradleException(
			"[HytalePublisher] Could not parse Hytale Maven metadata. The cached copy may be corrupt; " +
			"delete ${cacheFile} and try again.",
			e)
		}
	}

	private static String downloadText(String urlString) {
		def url = new URI(urlString).parseServerAuthority().toURL()
		def connection = url.openConnection()
		connection.connectTimeout = 10000
		connection.readTimeout = 15000
		connection.setRequestProperty('Accept', 'application/xml, text/xml, */*')
		connection.connect()
		int code = connection.responseCode
		if (code < 200 || code >= 300) {
			throw new IOException("HTTP ${code} from ${urlString}")
		}
		return connection.inputStream.withCloseable { stream ->
			stream.getText('UTF-8')
		}
	}

	private static String pickVersion(String selector, List<String> available) {
		if (available == null || available.isEmpty()) {
			return null
		}

		List<String> sorted = available.sort(false) { a, b -> compareVersions(a, b) }

		if (selector == '+' || selector.startsWith('latest.')) {
			return sorted.last()
		}

		String prefix = selector.substring(0, selector.length() - 1)
		def matching = sorted.findAll { it.startsWith(prefix) }
		return matching.isEmpty() ? null : matching.last()
	}

	private static int compareVersions(String a, String b) {
		String[] aParts = a.split(/[.\-]/)
		String[] bParts = b.split(/[.\-]/)
		int len = Math.min(aParts.length, bParts.length)
		for (int i = 0; i < len; i++) {
			int cmp = compareSegment(aParts[i], bParts[i])
			if (cmp != 0) {
				return cmp
			}
		}
		return Integer.compare(aParts.length, bParts.length)
	}

	private static int compareSegment(String a, String b) {
		boolean aNumeric = a.isInteger()
		boolean bNumeric = b.isInteger()
		if (aNumeric && bNumeric) {
			return Integer.compare(a.toInteger(), b.toInteger())
		}
		if (aNumeric) {
			return -1
		}
		if (bNumeric) {
			return 1
		}
		return a <=> b
	}
}
