package com.azuredoom.hytalepublisher

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.publish.maven.MavenPom

import javax.inject.Inject

@SuppressWarnings("unused")
class HytalePublisherExtension {
	final Property<String> version

	final Property<String> releaseType

	final Property<String> gameVersion

	final Property<String> changelogFile

	final ModtaleConfig    modtale

	final CurseForgeConfig curseforge

	final ModifoldConfig   modifold

	final ThunderstoreConfig thunderstore

	final GitHubConfig     github

	final MavenConfig      maven

	@Inject
	HytalePublisherExtension(Project project) {
		def objects = project.objects

		version       = objects.property(String).convention(project.provider { project.version.toString() })

		releaseType   = objects.property(String).convention("release")

		gameVersion = objects.property(String).convention(
				project.provider {
					project.hasProperty("hytale_version")
							? project.property("hytale_version").toString()
							: ""
				}
				)
		changelogFile = objects.property(String).convention("changelog.md")

		modtale      = objects.newInstance(ModtaleConfig)

		curseforge   = objects.newInstance(CurseForgeConfig)

		modifold     = objects.newInstance(ModifoldConfig)

		thunderstore = objects.newInstance(ThunderstoreConfig)

		github       = objects.newInstance(GitHubConfig)

		maven        = objects.newInstance(MavenConfig)

		def defaultPatchline = project.hasProperty("patchline")
				? project.property("patchline").toString()
				: "release"

		modtale.patchline = defaultPatchline

		modifold.patchline = defaultPatchline
	}

	void modtale(Action<ModtaleConfig> action)       {
		action.execute(modtale)
	}

	void curseforge(Action<CurseForgeConfig> action) {
		action.execute(curseforge)
	}

	void modifold(Action<ModifoldConfig> action)     {
		action.execute(modifold)
	}

	void thunderstore(Action<ThunderstoreConfig> action) {
		action.execute(thunderstore)
	}

	void github(Action<GitHubConfig> action) {
		action.execute(github)
	}

	void maven(Action<MavenConfig> action) {
		action.execute(maven)
	}
}

class Dependency {
	final String id

	final String version

	final boolean optional

	Dependency(String id, String version = null, boolean optional = false) {
		this.id       = id

		this.version  = version

		this.optional = optional
	}
}

@SuppressWarnings("unused")
class ModtaleConfig {
	boolean enabled = false

	String projectId = ""

	String apiKeyProp = "modTaleKey"

	String apiKeyEnv  = "MODTALE_KEY"

	String patchline = "release"

	boolean replaceExisting = false

	final List<Dependency> dependencies = []

	void required(String modId, String version) {
		dependencies << new Dependency(modId, version, false)
	}

	void optional(String modId, String version) {
		dependencies << new Dependency(modId, version, true)
	}
}

@SuppressWarnings("unused")
class CurseForgeConfig {
	boolean enabled = false

	String projectId = ""

	String apiKeyProp = "curseKey"

	String apiKeyEnv  = "CURSE_KEY"

	List<Integer> gameVersionIds = [14284]

	final List dependencies = []

	void required(String slug) {
		dependencies << new CurseForgeDependency(slug, "requiredDependency")
	}

	void optional(String slug) {
		dependencies << new CurseForgeDependency(slug, "optionalDependency")
	}

	void embeddedLibrary(String slug) {
		dependencies << new CurseForgeDependency(slug, "embeddedLibrary")
	}

	void embedded(String slug) {
		embeddedLibrary(slug)
	}

	void incompatible(String slug) {
		dependencies << new CurseForgeDependency(slug, "incompatible")
	}

	void tool(String slug) {
		dependencies << new CurseForgeDependency(slug, "tool")
	}
}

class CurseForgeDependency {
	final String slug

	final String type

	CurseForgeDependency(String slug, String type) {
		this.slug = slug

		this.type = type
	}
}

@SuppressWarnings("unused")
class ModifoldConfig {
	boolean enabled = false

	String projectId = ""

	String apiKeyProp = "modifoldKey"

	String apiKeyEnv = "MODIFOLD_KEY"

	List loaders = ["Vanilla"]

	List gameVersions = []

	String patchline = "release"

	final List dependencies = []

	void required(String slug, String versionId = null) {
		dependencies << new ModifoldDependency(slug, "required", versionId)
	}

	void optional(String slug, String versionId = null) {
		dependencies << new ModifoldDependency(slug, "optional", versionId)
	}

	void incompatible(String slug, String versionId = null) {
		dependencies << new ModifoldDependency(slug, "incompatible", versionId)
	}

	void embedded(String slug, String versionId = null) {
		dependencies << new ModifoldDependency(slug, "embedded", versionId)
	}
}

class ModifoldDependency {
	final String slug
	final String type
	final String versionId

	ModifoldDependency(String slug, String type, String versionId = null) {
		this.slug      = slug
		this.type      = type
		this.versionId = versionId
	}
}

@SuppressWarnings("unused")
class ThunderstoreConfig {
	boolean enabled = false

	String namespace      = ""

	String packageName    = ""

	String description    = ""

	String websiteUrl     = ""

	String community = "hytale"

	List<String> communities = []

	List<String> categories  = []

	boolean hasNsfwContent   = false

	String iconFile    = ""

	String readmeFile  = ""

	String apiKeyProp  = "thunderstoreToken"

	String apiKeyEnv   = "TCLI_AUTH_TOKEN"

	String repository  = "https://thunderstore.io"

	final List<String> dependencies   = []

	final Map<String, List<String>> contentBundles = [:]

	final List<String> extraIncludes  = []

	void dependency(String dependencyString) {
		dependencies << dependencyString
	}

	void dependency(String namespace, String packageName, String version) {
		dependencies << "${namespace}-${packageName}-${version}".toString()
	}

	void plugin(String path)      {
		addContent("mods", path)
	}

	void earlyPlugin(String path) {
		addContent("earlyplugins", path)
	}

	void assetPack(String path)   {
		addContent("mods", path)
	}

	void world(String path)       {
		addContent("worlds", path)
	}

	void universe(String path)    {
		addContent("universes", path)
	}

	void save(String path)        {
		addContent("saves", path)
	}

	void content(String folder, String path) {
		addContent(folder, path)
	}

	void include(String path) {
		extraIncludes << path
	}

	private void addContent(String folder, String path) {
		def list = contentBundles.get(folder)
		if (list == null) {
			list = []
			contentBundles.put(folder, list)
		}
		list << path
	}
}

@SuppressWarnings("unused")
class GitHubConfig {
	boolean enabled = false

	String repository = ""

	String apiKeyProp = "githubToken"

	String apiKeyEnv  = "GITHUB_TOKEN"

	String apiBaseUrl    = "https://api.github.com"

	String uploadBaseUrl = "https://uploads.github.com"

	String tagPrefix = "v"

	String targetCommitish = ""

	String releaseName = ""

	boolean draft      = false

	boolean prerelease = false

	boolean autoPrerelease = true

	boolean generateReleaseNotes = false

	String makeLatest = "true"

	String discussionCategoryName = ""

	boolean includeJar         = true

	boolean includeSourcesJar  = true

	boolean includeJavadocJar  = true

	String sourcesJarTaskName = "sourcesJar"

	String javadocJarTaskName = "javadocJar"

	final List<String> extraAssets = []

	void asset(String path) {
		extraAssets << path
	}
}

@SuppressWarnings("unused")
class MavenConfig {
	boolean enabled = false

	String url = ""

	String snapshotUrl = ""

	boolean allowInsecureProtocol = false

	String repositoryName   = "custom"

	String publicationName  = "maven"

	String groupId    = ""

	String artifactId  = ""

	String version    = ""

	String usernameProp = "mavenUsername"

	String usernameEnv  = "MAVEN_USERNAME"

	String passwordProp = "mavenPassword"

	String passwordEnv  = "MAVEN_PASSWORD"

	boolean includeJar        = true

	String jarTaskName        = "jar"

	boolean includeSourcesJar = true

	String sourcesJarTaskName = "sourcesJar"

	boolean includeJavadocJar = true

	String javadocJarTaskName = "javadocJar"

	String pomName        = ""

	String pomDescription = ""

	String pomUrl         = ""

	Action<MavenPom> pomAction = null

	final List<String> extraArtifactPaths = []

	void pom(Action<MavenPom> action) {
		pomAction = action
	}

	void artifact(String path) {
		extraArtifactPaths << path
	}
}