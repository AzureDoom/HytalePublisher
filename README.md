# HytalePublisher

[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/com.azuredoom.hytalepublisher?label=Gradle%20Plugin%20Portal)](https://plugins.gradle.org/plugin/com.azuredoom.hytalepublisher)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Gradle](https://img.shields.io/badge/Gradle-plugin-02303A.svg?logo=gradle)](https://gradle.org/)

HytalePublisher is a Gradle plugin for publishing Hytale mods to multiple hosting platforms, including Modtale, CurseForge, Modifold, Thunderstore, GitHub Releases, and custom Maven repositories.

It provides a single Gradle DSL for configuring release metadata, platform-specific publishing options, credentials, and dependencies. Each publishing platform is opt-in, so only the platforms you enable will register publish tasks.

---

## Features

- Publish Hytale mods to Modtale, CurseForge, Modifold, Thunderstore, GitHub Releases, and custom Maven repositories
- Enable only the publishing targets you need
- Configure shared release metadata with sensible platform-specific defaults
- Read API keys from `key.properties` or environment variables
- Keep project IDs in the Gradle DSL instead of credential files
- Define platform-specific dependencies on Modtale, CurseForge, and Modifold
- Tag the release commit and publish a GitHub release with the jar, sources jar, and javadoc jar attached
- Publish the jar (plus optional sources jar, javadoc jar, and extra artifacts) to any custom Maven repository, using Gradle's built-in `maven-publish` plugin under the hood
- Automatically run `build` before publishing
- Use `publishAll` to publish to every enabled platform
- Designed to support additional hosting platforms in the future

---

## Installation

### 1. Configure plugin repositories

If needed, add the Gradle Plugin Portal to your `settings.gradle` file:

```groovy
pluginManagement {
  repositories {
    gradlePluginPortal()
    mavenLocal()
    maven {
      url = uri("https://maven.azuredoom.com/mods")
    }
  }
}
```

### 2. Apply the plugin

Add the plugin to your `build.gradle` file:

```groovy
plugins {
  id 'com.azuredoom.hytalepublisher' version '1.1.10'
}
```

---

## Credentials

Create a `key.properties` file in your project root for local API keys.

Do not commit this file. HytalePublisher will automatically add `key.properties` to `.gitignore` when possible.

```properties
modTaleKey=your-modtale-api-key
curseKey=your-curseforge-api-token
modifoldKey=your-modifold-bearer-token
thunderstoreToken=your-thunderstore-service-account-token
githubToken=your-github-personal-access-token
mavenUsername=your-repo-username
mavenPassword=your-repo-password-or-token
```

Project IDs should be configured in the Gradle DSL, not in `key.properties`.

Unlike the other platforms, Maven credentials are a username/password pair rather than a single API key, and they're optional — if `mavenUsername` / `mavenPassword` (or their environment variable equivalents) aren't set, `publishToMaven` still runs and attempts an unauthenticated publish, which is fine for repositories that don't require auth.

You can get your API from the following links:
- Modtale: https://modtale.net/dashboard/developer
- CurseForge: https://legacy.curseforge.com/account/api-tokens
- Modifold: https://modifold.com/settings/api
- Thunderstore: <https://thunderstore.io/settings/teams/> → [your team] → Service Accounts → Add service account
- GitHub: <https://github.com/settings/tokens> → generate a token (classic or fine-grained) with `repo` / "Contents: Read and write" access to the target repository. In GitHub Actions, the built-in `GITHUB_TOKEN` works instead — see [CI/CD Example](#cicd-example).

### Environment variable support

Credentials can also be provided through environment variables. This is recommended for CI/CD workflows.

| Platform     | `key.properties` key | Environment variable |
|--------------|----------------------|----------------------|
| Modtale      | `modTaleKey`         | `MODTALE_KEY`        |
| CurseForge   | `curseKey`           | `CURSE_KEY`          |
| Modifold     | `modifoldKey`        | `MODIFOLD_KEY`       |
| Thunderstore | `thunderstoreToken`  | `TCLI_AUTH_TOKEN`    |
| GitHub       | `githubToken`        | `GITHUB_TOKEN`       |
| Maven        | `mavenUsername`      | `MAVEN_USERNAME`     |
| Maven        | `mavenPassword`      | `MAVEN_PASSWORD`     |

---

## Configuration

Add a `hytalePublisher` block to your `build.gradle` file.

```groovy
hytalePublisher {
  // Optional global release metadata
  version       = project.version

  releaseType   = "release"       // "release" | "beta" | "alpha"

  // Defaults to project.hytale_version if present.
  // Accepts dynamic selectors like "0.+" — see Game Version below.
  // gameVersion = project.hytale_version

  changelogFile = "changelog.md"  // Relative to the root project directory
  // Created automatically if missing
  // Existing files are matched case-insensitively, e.g. CHANGELOG.md

  modtale {
    enabled   = true
    projectId = "your-modtale-project-id"

    // Hytale patchline used to resolve dynamic gameVersion selectors.
    // Has no effect when gameVersion is set to a concrete version.
    // patchline = "release"   // or "pre-release"

    // Optional credential key overrides
    // apiKeyProp = "modTaleKey"
    // apiKeyEnv  = "MODTALE_KEY"

    // When true, re-uploading the same versionNumber for overlapping gameVersions
    // replaces the existing version on those targets instead of failing.
    // Non-overlapping targets on the old entry are not affected.
    // replaceExisting = false

    // Dependencies: required(modId, minVersion) / optional(modId, minVersion)
    required "5e9bbea3-0d7f-4365-93df-5e7acfadf0e7", "1.0.4"
    optional "2ebf130e-2189-4e90-9323-803a374d05ce", "1.5.2"
  }

  curseforge {
    enabled   = true
    projectId = "123456" // Your CurseForge numeric project ID

    // Optional game version ID override, only for advanced users
    // gameVersionIds = [14284]

    // Dependencies: required / optional / embeddedLibrary / incompatible / tool
    required "levelingcore"
    optional "dynamictooltipslib"
    embeddedLibrary "bundled-helper"
    incompatible "broken-addon"
    tool "dev-tooling-helper"
  }

  modifold {
    enabled   = true
    projectId = "your-modifold-project-slug"

    // Must exactly match the game version names shown by Hytale / Modifold.
    // Can contain one or many supported versions.
    gameVersions = [
            "0.5.0-pre.9.1",
            "0.5.0-pre.9",
            "0.5.0-pre.8",
            "0.5.0-pre.7"
    ]

    // Must be a list; automatically JSON-encoded for the API.
    loaders = ["Vanilla"]

    // Dependencies: required / optional / incompatible / embedded
    // Second argument is an optional Modifold version_id; omit for "any version"
    required     "mermaids", "oCK3bg"
    optional     "prettier-than-before"
    incompatible "broken-mod"
    embedded     "bundled-helper", "abc123"
  }

  thunderstore {
    enabled    = true

    // Required: the Thunderstore team (namespace) you upload under
    namespace  = "YourTeam"

    // Optional: defaults to project.name with spaces -> underscores
    // packageName = "Your_Mod_Name"

    // Optional
    // websiteUrl = "link_to_your_sources"

    // Optional: defaults to project.description, max 250 chars
    // description = "A short description of the mod."

    // Defaults to "hytale" — the Thunderstore community slug for Hytale
    // community = "hytale"

    // Tag the package per the Hytale community categories. Browse at
    // https://thunderstore.io/api/experimental/community/hytale/category/
    categories = ["plugins", "mods", "release"]

    // Required by Thunderstore TOS if applicable
    // hasNsfwContent = false

    // Dependencies in Thunderstore "Namespace-PackageName-Version" format
    dependency "Hytale", "HytaleAPI", "8.8.1"
    dependency "Hytale-HytaleAPI-8.8.1"  // alternative single-string form

    // --- Content bundling -------------------------------------------------
    // Each helper places a file/folder into the Thunderstore-required folder
    // structure inside the package zip:
    //
    //   plugin(path)      -> mods/<name>.jar
    //   earlyPlugin(path) -> earlyplugins/<name>.jar
    //   assetPack(path)   -> mods/<name>.zip
    //   world(path)       -> worlds/<dir>
    //   universe(path)    -> universes/<dir>
    //   save(path)        -> saves/<dir>
    //
    // If you don't call any of these, the plugin's built jar is auto-placed
    // into mods/ — matching the Hytale Modding Thunderstore plugin guide.
    //
    // plugin "build/libs/MyMod-${project.version}.jar"
    // world  "src/main/resources/worlds/my-cool-world"
  }

  github {
    enabled = true

    // Optional: "owner/repo". Auto-detected from the "origin" git remote if omitted.
    // repository = "AzureDoom/Ovomorphosis"

    // Optional credential key overrides
    // apiKeyProp = "githubToken"
    // apiKeyEnv  = "GITHUB_TOKEN"

    // Tag applied to the release commit, e.g. "v1.0.0". The tag is created
    // automatically by GitHub as part of creating the release — no separate
    // git tag/push step is needed.
    // tagPrefix = "v"

    // Optional: commit or branch the tag points at. Defaults to the current HEAD.
    // targetCommitish = "main"

    // Optional: release title. Defaults to "<projectName> <projectVersion>".
    // releaseName = ""

    // draft      = false
    // prerelease = false

    // When true (default), releaseType values other than "release" (e.g. "beta",
    // "alpha") automatically mark the GitHub release as a prerelease.
    // autoPrerelease = true

    // Let GitHub append its auto-generated notes after the changelog body.
    // generateReleaseNotes = false

    // Mirrors GitHub's make_latest release field: "true" | "false" | "legacy"
    // makeLatest = "true"

    // Optional: opens a linked discussion for the release under this category.
    // discussionCategoryName = ""

    // Attach the built jar, sources jar, and javadoc jar. Sources/javadoc are
    // skipped automatically (with a warning) if those tasks aren't present.
    // includeJar        = true
    // includeSourcesJar = true
    // includeJavadocJar = true

    // Only relevant if your sourcesJar/javadocJar tasks use non-standard names
    // sourcesJarTaskName = "sourcesJar"
    // javadocJarTaskName = "javadocJar"

    // Attach any additional files to the release
    // asset "build/libs/extra-debug-symbols.zip"
  }

  maven {
    enabled = true

    // Required: where release versions get uploaded
    url = "https://maven.azuredoom.com/mods"

    // Optional: used instead of `url` when the resolved version ends with "-SNAPSHOT"
    // snapshotUrl = "https://maven.azuredoom.com/mods-snapshots"

    // Optional: allow plain http:// repository URLs (disabled by default for safety)
    // allowInsecureProtocol = false

    // Optional credential key overrides
    // usernameProp = "mavenUsername"
    // usernameEnv  = "MAVEN_USERNAME"
    // passwordProp = "mavenPassword"
    // passwordEnv  = "MAVEN_PASSWORD"

    // Optional: defaults to project.group / project.name / hytalePublisher.version
    // groupId    = "com.azuredoom"
    // artifactId = "levelingcore"
    // version    = project.version

    // Optional: names for the Gradle publication and repository. Only relevant
    // if you need to reference them elsewhere in your build.
    // publicationName = "maven"
    // repositoryName  = "custom"

    // Attach the built jar, sources jar, and javadoc jar. Sources/javadoc are
    // skipped automatically (with a warning) if those tasks aren't present.
    // includeJar        = true
    // includeSourcesJar = true
    // includeJavadocJar = true

    // Only relevant if your jar/sourcesJar/javadocJar tasks use non-standard names
    // jarTaskName        = "jar"
    // sourcesJarTaskName = "sourcesJar"
    // javadocJarTaskName = "javadocJar"

    // Attach any additional files to the publication
    // artifact "build/libs/extra-debug-symbols.zip"

    // Optional POM metadata
    // pomName        = "LevelingCore"
    // pomDescription = "Rendering and animation library for Hytale mods"
    // pomUrl         = "https://github.com/AzureDoom/LevelingCore"

    // Escape hatch for anything not covered above — delegates to Gradle's
    // MavenPom directly (licenses, developers, SCM info, etc.)
    // pom { pom ->
    //   pom.licenses {
    //     license {
    //       name = "MIT License"
    //       url  = "https://opensource.org/licenses/MIT"
    //     }
    //   }
    // }
  }
}
```

### Game Version

By default, HytalePublisher uses the `hytale_version` project property if present.

You can define this in `gradle.properties`:

```properties
hytale_version=your-game-version
```

If `hytale_version` is not set, some platforms (such as Modtale) may reject uploads due to invalid game version values.

This value is used for platforms like Modtale that require a specific version string.

You typically do not need to set `gameVersion` manually unless you want to override this behavior.

#### Dynamic version selectors

`gameVersion` accepts Gradle-style dynamic version selectors so you can track the latest Hytale build without updating `gradle.properties` for every server release:

```groovy
hytalePublisher {
  gameVersion = "0.+"   // latest 0.x build on the configured patchline

  modtale {
    enabled   = true
    projectId = "your-modtale-project-id"
    patchline = "release"   // or "pre-release"
  }
}
```

Supported selectors:

- `0.+` — latest version starting with `0.`
- `0.04.+` — latest version starting with `0.04.` (note: prefix matching is literal, including leading zeros)
- `+` — absolute latest version
- `latest.release` — same as `+`

Range syntax (e.g. `[0.5,0.6)`) is not supported. Use a prefix selector or a concrete version instead.

The selector is resolved at publish time by querying the Hytale Maven metadata for the configured `patchline`. The resolved concrete version (e.g. `0.5.4`) is what gets uploaded to Modtale, so your published mod stays pinned to a specific server build.

#### Patchline scoping

The `modtale.patchline` field controls which Hytale Maven repository the resolver queries:

- `patchline = "release"` (default) resolves against `https://maven.hytale.com/release`
- `patchline = "pre-release"` resolves against `https://maven.hytale.com/pre-release`

`patchline` only affects dynamic selector resolution. When `gameVersion` is a concrete value it is uploaded as-is regardless of patchline.

#### Cache behavior

Maven metadata is cached under `<gradle-user-home>/caches/hytale-publisher/` for ten minutes to avoid repeated network calls during a publishing session. To force a fresh fetch (for example, immediately after a new server build is published), delete the cache file for your patchline:

```bash
# Linux / macOS
rm ~/.gradle/caches/hytale-publisher/maven-metadata-release.xml

# Or just clear the whole directory
rm -rf ~/.gradle/caches/hytale-publisher
```

If the network is unreachable but a cached copy exists, the resolver falls back to the cache with a warning. If nothing is cached and the network is down, publishing fails with a clear error rather than guessing.

#### Standalone resolution

Wildcard resolution does not require the [Hytale Tools](https://github.com/AzureDoom/Hytale-Gradle-Plugin) Gradle plugin to be applied. HytalePublisher fetches version metadata directly from the Hytale Maven, so you can use `0.+` even in projects that build with a different toolchain.

---

## Minimal Configuration

For a simple setup, apply the plugin, define your Hytale version, and enable the platforms you want to publish to.
```groovy
plugins {
  id "com.azuredoom.hytalepublisher" version "1.1.10"
}

version = "1.0.0"
// Define your Hytale version (for example in `build.gradle` or `gradle.properties`):
ext.hytale_version = "your-game-version"

hytalePublisher {
  modtale {
    enabled = true
    projectId = "your-modtale-project-id"
  }

  curseforge {
    enabled = true
    projectId = "123456"
  }

  modifold {
    enabled = true
    projectId = "your-modifold-project-slug"
    gameVersions = [project.hytale_version]
    loaders = ["vanilla"]
  }
}
```

Then publish to all enabled platforms:
```bash
./gradlew publishAll
```

---

## Publishing a Release

Follow these steps when preparing and publishing a new Hytale mod release.

### 1. Update your project version

Set the release version in your Gradle build, for example:

```groovy
version = "1.0.0"
```

HytalePublisher uses `project.version` by default unless you override `hytalePublisher.version`.

### 2. Update your changelog

Create or update the changelog file configured by `changelogFile`.

By default, HytalePublisher uses:

```text
changelog.md
```

If the configured changelog file does not exist, HytalePublisher creates it automatically with a basic Markdown heading.

Changelog file names are matched case-insensitively. For example, all of these are treated as valid matches for the default `changelog.md`:

```text
changelog.md
CHANGELOG.md
CHANGELOg.md
```

The path is resolved relative to the root project directory unless an absolute path is provided.

### 3. Configure the platforms you want to publish to

Enable only the platforms you want to publish this release to:

```groovy
hytalePublisher {
  modtale {
    enabled = true
    projectId = "your-modtale-project-id"
  }

  curseforge {
    enabled = true
    projectId = "123456"
  }

  modifold {
    enabled = false
    projectId = "your-modifold-project-slug"
  }
}
```

Only enabled platforms will register publish tasks.

### 4. Add credentials

For local publishing, add API keys to `key.properties`:

```properties
modTaleKey=your-modtale-api-key
curseKey=your-curseforge-api-token
modifoldKey=your-modifold-bearer-token
```

For CI/CD publishing, provide the matching environment variables instead.

### 5. Build and publish

Publish to a single platform:

```bash
./gradlew publishToModtale
./gradlew publishToCurseForge
./gradlew publishToModifold
```

Publish to every enabled platform:

```bash
./gradlew publishAll
```

Use `--info` or `--stacktrace` if you need detailed error output during publishing.

All publishing tasks automatically depend on `build`, so your mod artifact is built before upload.

---

## Example Output

A successful `publishAll` run may look similar to this:

```text
> Task :publishToCurseForge
{"id":7980260}
[HytalePublisher] Successfully published to CurseForge: Classescore 0.1.1-beta

> Task :publishToModifold
{"success":true,"versionId":"e5CC8A","fileUrl":"https://media.modifold.com/projects/Ff5L4Q/Classescore-0.1.1-beta_fb088fdb.jar"}
[HytalePublisher] Successfully published to Modifold: Classescore 0.1.1-beta

> Task :publishToModtale
[HytalePublisher] Resolved gameVersion '0.+' to '0.5.4' against patchline 'release'.
[HytalePublisher] Successfully published to Modtale: Classescore 0.1.1-beta

> Task :publishAll
```

Some platforms return a JSON response before the success message. This indicates the upload was accepted and processed by the platform.

If a platform rejects the upload (e.g. invalid credentials, bad project ID, or a version conflict), the build fails with the HTTP status code and the full error response from the platform:

```text
> Task :publishToModtale FAILED

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':publishToModtale'.
> [HytalePublisher] Modtale upload failed with HTTP 403.

  {"detail":"You do not have permission to perform this action with the current account or API key.",...}
```

---

## CI/CD Example

HytalePublisher supports environment variables for API keys, which makes it suitable for CI/CD workflows.

### GitHub Actions

```yaml
name: Publish Mod

on:
  workflow_dispatch:
  release:
    types: [published]

jobs:
  publish:
    runs-on: ubuntu-latest

    permissions:
      contents: write  # required for the github {} publish target to create releases/tags

    env:
      MODTALE_KEY: ${{ secrets.MODTALE_KEY }}
      CURSE_KEY: ${{ secrets.CURSE_KEY }}
      MODIFOLD_KEY: ${{ secrets.MODIFOLD_KEY }}
      TCLI_AUTH_TOKEN: ${{ secrets.TCLI_AUTH_TOKEN }}
      GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
      MAVEN_USERNAME: ${{ secrets.MAVEN_USERNAME }}
      MAVEN_PASSWORD: ${{ secrets.MAVEN_PASSWORD }}

    steps:
      - name: Checkout repository
        uses: actions/checkout@v6
        with:
          fetch-depth: 0  # ensures the full history/tags are available for the github {} target

      - name: Set up Java
        uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: 25

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v5

      - name: Publish enabled platforms
        run: |
          chmod +x ./gradlew
          ls -l ./gradlew
          ./gradlew publishAll
```

Only platforms with `enabled = true` will publish. Store API keys as repository or organization secrets. `GITHUB_TOKEN` is provided automatically by GitHub Actions — you don't need to create it as a secret yourself, but the job does need `permissions: contents: write` for it to be allowed to create releases and tags.

---

## Multi-Module Projects

In a multi-module Gradle project, apply HytalePublisher only to the subproject that produces the mod JAR.

### Root `settings.gradle`
```gradle
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenLocal()
        maven {
            url = uri("https://maven.azuredoom.com/mods")
        }
    }
}

rootProject.name = "my-hytale-mods"

include "core"
include "addon"
```

### Root `build.gradle`
```gradle
subprojects {
    group = "com.example.hytale"
    version = "1.0.0"

    ext.hytale_version = "your-game-version"
}
```

### Mod subproject (`core/build.gradle`)
```gradle
plugins {
    id "java"
    id "com.azuredoom.hytalepublisher" version "1.1.10"
}

hytalePublisher {
    releaseType = "release"
    changelogFile = "changelog.md"

    modtale {
        enabled = true
        projectId = "your-modtale-project-id"
    }

    curseforge {
        enabled = true
        projectId = "123456"
    }

    modifold {
        enabled = true
        projectId = "your-modifold-project-slug"
        gameVersions = [project.hytale_version]
        loaders = ["vanilla"]
    }
}
```

Run publishing from the root project with the subproject path:

```bash
./gradlew :core:publishAll
```

Or publish to a single platform:

```bash
./gradlew :core:publishToModtale
```

Each publish task uses the JAR built by the subproject where the plugin is applied.

---

## Tasks

| Task                    | Description                                                                                       | Registered when                  |
|-------------------------|---------------------------------------------------------------------------------------------------|----------------------------------|
| `publishToModtale`      | Uploads the built JAR and changelog to Modtale                                                    | `modtale.enabled = true`         |
| `publishToCurseForge`   | Uploads the built JAR and changelog to CurseForge                                                 | `curseforge.enabled = true`      |
| `publishToModifold`     | Uploads the built JAR and changelog to Modifold                                                   | `modifold.enabled = true`        |
| `publishToThunderstore` | Builds a Thunderstore package zip and uploads it to thunderstore.io                               | `thunderstore.enabled = true`    |
| `publishToGitHub`       | Tags the release commit and publishes a GitHub release with the jar, sources jar, and javadoc jar | `github.enabled = true`          |
| `publishToMaven`        | Publishes the jar (plus optional sources/javadoc/extra artifacts) to the configured Maven repo    | `maven.enabled = true`           |
| `publishAll`            | Runs all enabled publishing tasks                                                                 | At least one platform is enabled |

---

## Credential and Project ID Reference

| Platform     | API key source                                                                  | Project ID location                                            |
|--------------|---------------------------------------------------------------------------------|----------------------------------------------------------------|
| Modtale      | `modTaleKey` or `MODTALE_KEY`                                                   | `modtale.projectId`                                            |
| CurseForge   | `curseKey` or `CURSE_KEY`                                                       | `curseforge.projectId`                                         |
| Modifold     | `modifoldKey` or `MODIFOLD_KEY`                                                 | `modifold.projectId`                                           |
| Thunderstore | `thunderstoreToken` or `TCLI_AUTH_TOKEN`                                        | `thunderstore.namespace` / `thunderstore.packageName`          |
| GitHub       | `githubToken` or `GITHUB_TOKEN`                                                 | `github.repository` (auto-detected from git remote if omitted) |
| Maven        | `mavenUsername`/`mavenPassword` or `MAVEN_USERNAME`/`MAVEN_PASSWORD` (optional) | `maven.url`                                                    |

API key property names and environment variable names can be customized in the DSL using `apiKeyProp` and `apiKeyEnv` (or `usernameProp`/`usernameEnv`/`passwordProp`/`passwordEnv` for Maven).

You can get your API from the following links:
- Modtale: https://modtale.net/dashboard/developer
- CurseForge: https://authors.curseforge.com/#/settings/api-tokens
- Modifold: https://modifold.com/settings/api
- GitHub: https://github.com/settings/tokens

---

## Notes

- `key.properties` should contain API keys only.
- Project IDs belong in the Gradle DSL.
- Changelog paths are resolved relative to the root project directory unless an absolute path is provided.
- If the configured changelog file is missing, HytalePublisher creates it automatically.
- Changelog file names are matched case-insensitively, so `CHANGELOG.md`, `changelog.md`, and similar capitalization variants are accepted.
- Dependency configuration is platform-specific because each hosting service supports different dependency metadata.

---

## Platform Notes

### Modtale

- Uses `gameVersion` (defaults to `project.hytale_version`)
- Ensure your `hytale_version` matches a valid Modtale-supported version
- Accepts dynamic selectors like `0.+` — see [Game Version](#game-version) for details
- Use `modtale.patchline` (`"release"` or `"pre-release"`) to scope dynamic resolution to a specific Hytale Maven repo
- `releaseType` is automatically normalized to uppercase (`RELEASE`, `BETA`, `ALPHA`) before upload — lowercase or mixed-case values in the DSL are accepted
- Upload failures (4xx/5xx responses) fail the build immediately with the HTTP status code and full error body, so misconfigured credentials or project IDs are caught before the success message is printed
- Set `replaceExisting = true` to allow re-uploading the same `versionNumber` for overlapping `gameVersions` — matching targets are replaced in place. Targets from the existing version that do not overlap are left unchanged. Defaults to `false`.

### CurseForge

- Uses numeric `gameVersionIds`
- Defaults to `[14284]` (Hytale Early Access)

### Modifold

- `gameVersions` and `loaders` must be arrays.
- `gameVersions` must contain exact Hytale game version names, for example `0.5.0-pre.9.1`.
- The old `Early Access` version category is no longer valid.
- If `modifold.gameVersions` is omitted, the plugin falls back to `hytalePublisher.gameVersion` / `project.hytale_version`.
- Multiple supported game versions can be uploaded at once.
- These are automatically JSON-encoded by the plugin
- Supports four dependency types via the DSL:
  - `required(slug, versionId?)` — the dependency is required for this build to work
  - `optional(slug, versionId?)` — the dependency is optional
  - `incompatible(slug, versionId?)` — this build is incompatible with the dependency
  - `embedded(slug, versionId?)` — the dependency is bundled inside this build
- The `versionId` argument is optional; omit it to allow any version of the dependency

### Thunderstore

- Authentication uses **Thunderstore service-account API tokens**, not your user account. Generate one at: `thunderstore.io -> Settings -> Teams -> [your team] -> Service Accounts`.
- The package version must be SemVer (`MAJOR.MINOR.PATCH`). If your project version includes a qualifier like `-beta`, the plugin strips it for the Thunderstore manifest. Once a version is uploaded it cannot be reused, bump your version for every release.
- The plugin auto-generates `manifest.json` inside the zip from your DSL.
  You don't need to maintain a manifest in your repo, but you DO need:
  - `icon.png` (256x256 PNG) at the project root
  - `README.md` at the project root
    ...both are required by Thunderstore.
- `hytalePublisher.changelogFile` is bundled as `CHANGELOG.md` inside the package zip. If the file is missing, HytalePublisher creates it automatically. Existing changelog files are matched case-insensitively.
- The Hytale community slug is `hytale`. Browse available category slugs at:
  https://thunderstore.io/api/experimental/community/hytale/category/
- Content folder conventions match the Hytale Modding Thunderstore guides:
  - Plugins (.jar)        -> `mods/`
  - Early plugins (.jar)  -> `earlyplugins/`
  - Asset packs (.zip)    -> `mods/`
  - Worlds                -> `worlds/`
  - Universes             -> `universes/`
  - Saves                 -> `saves/`
- Once a package is uploaded, its `name` and `team` are immutable. Triple check both before your first publish.

### GitHub

- Creates a GitHub Release via the REST API. The release's `tag_name` is created automatically as part of creating the release — there's no separate tag/push step, and no way to publish a release without a tag (that's how GitHub Releases work).
- `github.repository` ("owner/repo") is optional. If omitted, it's auto-detected from the `origin` git remote (both SSH and HTTPS remote URLs are supported).
- The tag defaults to `tagPrefix + projectVersion`, e.g. `v1.0.0`. Override with `github.tagPrefix`.
- `github.targetCommitish` defaults to the current `HEAD` commit SHA (resolved via `git rev-parse HEAD`). Set it explicitly if publishing from a detached or shallow checkout where `git` commands might not resolve as expected — shallow checkouts (`fetch-depth: 1`) can still resolve `HEAD`, but full history (`fetch-depth: 0`) is recommended for reliability.
- Attaches the built jar, sources jar, and javadoc jar as release assets. Sources/javadoc are skipped with a warning (not a failure) if no `sourcesJar` / `javadocJar` task is found — this matches the task names produced by Gradle's `java.withSourcesJar()` / `withJavadocJar()`.
- Additional files can be attached with `asset("path/to/file")`, resolved relative to the project directory unless given as an absolute path.
- `releaseType` values other than `"release"` (e.g. `"beta"`, `"alpha"`) automatically mark the GitHub release as a prerelease. Disable this with `autoPrerelease = false`, or force it with `prerelease = true`.
- Upload failures (4xx/5xx responses) fail the build immediately with the HTTP status code and full error body from GitHub.
- **Workflow trigger caution:** if your CI workflow triggers on `release: types: [published]` (as in the [CI/CD Example](#cicd-example) above) and you also enable `github.enabled = true`, publishing will try to create *another* release for a tag that already exists, which fails. Either trigger the workflow on `push: tags` / `workflow_dispatch` instead, or keep `github.enabled = false` in workflows meant to run in response to a release you already created manually.

### Maven

- Uses Gradle's built-in `maven-publish` plugin under the hood rather than a custom uploader — `maven.enabled = true` applies that plugin automatically, so you don't need to add it yourself.
- `maven.url` is required. If `maven.snapshotUrl` is also set and the resolved `version` ends with `-SNAPSHOT`, that URL is used instead — the common release/snapshot repository split.
- `groupId`, `artifactId`, and `version` default to `project.group`, `project.name`, and `hytalePublisher.version` respectively. Override any of them individually if your published coordinates should differ from the project's own.
- Attaches the built jar by default. `includeSourcesJar` / `includeJavadocJar` attach `sourcesJar` / `javadocJar` task outputs if present, and are skipped with a warning (not a failure) otherwise — this matches Gradle's `java.withSourcesJar()` / `withJavadocJar()` task names, same as the GitHub target.
- Additional files can be attached with `artifact("path/to/file")`, resolved relative to the project directory unless given as an absolute path.
- The generated POM only includes what you set via `pomName` / `pomDescription` / `pomUrl` — it does **not** pull in your project's dependencies, since Hytale-mapped dependencies generally aren't resolvable through a normal Maven `<dependencies>` block anyway. For anything else (licenses, developers, SCM info), use the `pom { }` escape hatch, which is handed Gradle's `MavenPom` directly.
- Credentials are optional — if `mavenUsername` / `mavenPassword` (or their env equivalents) aren't set, HytalePublisher logs an info message and attempts to publish without credentials, which works fine for repositories that permit unauthenticated writes.
- `allowInsecureProtocol` must be set to `true` if your repository URL is plain `http://` rather than `https://`. It defaults to `false`.
- Under the hood, `publishToMaven` is an alias for Gradle's auto-generated `publish<PublicationName>PublicationTo<RepositoryName>Repository` task, so it composes normally with other Gradle `maven-publish` tooling if you need it.
- **Shadow plugin caution:** if you use `com.gradleup.shadow` (or `com.github.johnrengelman.shadow`) and set `shadowJar.archiveClassifier.set('')` to make the shaded jar your "final" build output, `shadowJar` and the plain `jar` task now write to the *same* file. Gradle's task validation will fail with something like `uses this output of task ':jar'/':shadowJar' without declaring an explicit or implicit dependency`, because it can't tell which task actually produced the file being published. Fix it with two changes:
  - Set `maven.jarTaskName = "shadowJar"` so the plugin publishes (and depends on) the shaded jar rather than the plain one.
  - Give the plain `jar` task a distinct classifier so it no longer collides, e.g. `jar { archiveClassifier.set('slim') }`.

  This doesn't just silence the validation error — without it, there's a real risk of publishing the unshaded jar if task ordering ever shifts.

---

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.