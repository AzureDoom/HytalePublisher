package com.azuredoom.hytalepublisher

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.provider.Property

import javax.inject.Inject

class HytalePublisherExtension {
	final Property<String> version
	final Property<String> releaseType
	final Property<String> gameVersion
	final Property<String> changelogFile

	final ModtaleConfig    modtale
	final CurseForgeConfig curseforge
	final ModifoldConfig   modifold
	final ThunderstoreConfig thunderstore

	@Inject
	HytalePublisherExtension(Project project) {
		def objects = project.objects

		version       = objects.property(String).convention(project.provider { project.version.toString() })
		releaseType   = objects.property(String).convention("release")
		gameVersion = objects.property(String).convention(
				project.provider {
					project.hasProperty("hytale_version")
							? project.property("hytale_version").toString()
							: "Early Access"
				}
				)
		changelogFile = objects.property(String).convention("changelog.md")

		modtale      = objects.newInstance(ModtaleConfig)
		curseforge   = objects.newInstance(CurseForgeConfig)
		modifold     = objects.newInstance(ModifoldConfig)
		thunderstore = objects.newInstance(ThunderstoreConfig)
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

class ModtaleConfig {
	boolean enabled = false
	String projectId = ""
	String apiKeyProp = "modTaleKey"
	String apiKeyEnv  = "MODTALE_KEY"
	String patchline = "release"
	final List<Dependency> dependencies = []

	void required(String modId, String version) {
		dependencies << new Dependency(modId, version, false)
	}

	void optional(String modId, String version) {
		dependencies << new Dependency(modId, version, true)
	}
}

class CurseForgeConfig {
	boolean enabled = false
	String projectId = ""
	String apiKeyProp = "curseKey"
	String apiKeyEnv  = "CURSE_KEY"
	List<Integer> gameVersionIds = [14284]
	final List<Dependency> dependencies = []

	void required(String slug) {
		dependencies << new Dependency(slug, null, false)
	}

	void optional(String slug) {
		dependencies << new Dependency(slug, null, true)
	}
}

class ModifoldConfig {
	boolean enabled = false
	String projectId = ""
	String apiKeyProp = "modifoldKey"
	String apiKeyEnv  = "MODIFOLD_KEY"
	List<String> loaders      = ["vanilla"]
	List<String> gameVersions = ["Early Access"]
	final List<ModifoldDependency> dependencies = []

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