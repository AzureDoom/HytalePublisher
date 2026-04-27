# HytalePublisher

[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/com.azuredoom.hytalepublisher?label=Gradle%20Plugin%20Portal)](https://plugins.gradle.org/plugin/com.azuredoom.hytalepublisher)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Gradle](https://img.shields.io/badge/Gradle-plugin-02303A.svg?logo=gradle)](https://gradle.org/)

HytalePublisher is a Gradle plugin for publishing Hytale mods to multiple hosting platforms, including Modtale, CurseForge, and Modifold.

It provides a single Gradle DSL for configuring release metadata, platform-specific publishing options, credentials, and dependencies. Each publishing platform is opt-in, so only the platforms you enable will register publish tasks.

---

## Features

- Publish Hytale mods to Modtale, CurseForge, and Modifold
- Enable only the publishing targets you need
- Configure shared release metadata with sensible platform-specific defaults
- Read API keys from `key.properties` or environment variables
- Keep project IDs in the Gradle DSL instead of credential files
- Define platform-specific dependencies where supported
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
    id 'com.azuredoom.hytalepublisher' version '1.0.0'
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
```

Project IDs should be configured in the Gradle DSL, not in `key.properties`.

You can get your API from the following links:
- Modtale: https://modtale.net/dashboard/developer
- CurseForge: https://legacy.curseforge.com/account/api-tokens
- Modifold: https://modifold.com/settings/api

### Environment variable support

Credentials can also be provided through environment variables. This is recommended for CI/CD workflows.

| Platform   | `key.properties` key | Environment variable |
|------------|----------------------|----------------------|
| Modtale    | `modTaleKey`         | `MODTALE_KEY`        |
| CurseForge | `curseKey`           | `CURSE_KEY`          |
| Modifold   | `modifoldKey`        | `MODIFOLD_KEY`       |

---

## Configuration

Add a `hytalePublisher` block to your `build.gradle` file.

```groovy
hytalePublisher {
    // Optional global release metadata
    version       = project.version

    releaseType   = "release"       // "release" | "beta" | "alpha"

    // Defaults to project.hytale_version if present.
    // Accepts dynamic selectors like "2026.+" — see Game Version below.
    // gameVersion = project.hytale_version

    changelogFile = "changelog.md"  // Relative to the root project directory

    modtale {
        enabled   = true
        projectId = "your-modtale-project-id"

        // Hytale patchline used to resolve dynamic gameVersion selectors.
        // Has no effect when gameVersion is set to a concrete version.
        // patchline = "release"   // or "pre-release"

        // Optional credential key overrides
        // apiKeyProp = "modTaleKey"
        // apiKeyEnv  = "MODTALE_KEY"

        // Dependencies: required(modId, minVersion) / optional(modId, minVersion)
        required "5e9bbea3-0d7f-4365-93df-5e7acfadf0e7", "1.0.4"
        optional "2ebf130e-2189-4e90-9323-803a374d05ce", "1.5.2"
    }

    curseforge {
        enabled   = true
        projectId = "123456" // Your CurseForge numeric project ID

        // Optional game version ID override
        // gameVersionIds = [14284]

        // Dependencies: required(slug) / optional(slug)
        required "levelingcore"
        optional "dynamictooltipslib"
    }

    modifold {
        enabled   = true
        projectId = "your-modifold-project-slug"

        // Optional metadata overrides
        // Must be lists — automatically converted to JSON arrays for the API
        // loaders      = ["vanilla"]
        // gameVersions = [project.hytale_version]

        // Modifold dependencies are not currently supported.
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
    gameVersion = "2026.+"   // latest 2026.x build on the configured patchline

    modtale {
        enabled   = true
        projectId = "your-modtale-project-id"
        patchline = "release"   // or "pre-release"
    }
}
```

Supported selectors:

- `2026.+` — latest version starting with `2026.`
- `2026.04.+` — latest version starting with `2026.04.` (note: prefix matching is literal, including leading zeros)
- `+` — absolute latest version
- `latest.release` — same as `+`

Range syntax (e.g. `[2026.0,2027.0)`) is not supported. Use a prefix selector or a concrete version instead.

The selector is resolved at publish time by querying the Hytale Maven metadata for the configured `patchline`. The resolved concrete version (e.g. `2026.04.23-937872667`) is what gets uploaded to Modtale, so your published mod stays pinned to a specific server build.

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

Wildcard resolution does not require the [Hytale Tools](https://github.com/AzureDoom/Hytale-Gradle-Plugin) Gradle plugin to be applied. HytalePublisher fetches version metadata directly from the Hytale Maven, so you can use `2026.+` even in projects that build with a different toolchain.

---

## Minimal Configuration

For a simple setup, apply the plugin, define your Hytale version, and enable the platforms you want to publish to.
```groovy
plugins {
    id "com.azuredoom.hytalepublisher" version "1.0.0"
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

By default, HytalePublisher reads:

```text
changelog.md
```

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
[HytalePublisher] Successfully published to Modtale: Classescore 0.1.1-beta

> Task :publishAll
```

Some platforms return a JSON response before the success message. This indicates the upload was accepted and processed by the platform.

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

    env:
      MODTALE_KEY: ${{ secrets.MODTALE_KEY }}
      CURSE_KEY: ${{ secrets.CURSE_KEY }}
      MODIFOLD_KEY: ${{ secrets.MODIFOLD_KEY }}

    steps:
      - name: Checkout repository
        uses: actions/checkout@v6

      - name: Set up Java
        uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: 25

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v5

      - name: Publish enabled platforms
        run: ./gradlew publishAll
```

Only platforms with `enabled = true` will publish. Store API keys as repository or organization secrets.

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
    id "com.azuredoom.hytalepublisher" version "1.0.0"
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

| Task                  | Description                                       | Registered when                  |
|-----------------------|---------------------------------------------------|----------------------------------|
| `publishToModtale`    | Uploads the built JAR and changelog to Modtale    | `modtale.enabled = true`         |
| `publishToCurseForge` | Uploads the built JAR and changelog to CurseForge | `curseforge.enabled = true`      |
| `publishToModifold`   | Uploads the built JAR and changelog to Modifold   | `modifold.enabled = true`        |
| `publishAll`          | Runs all enabled publishing tasks                 | At least one platform is enabled |

---

## Credential and Project ID Reference

| Platform   | API key source                  | Project ID location    |
|------------|---------------------------------|------------------------|
| Modtale    | `modTaleKey` or `MODTALE_KEY`   | `modtale.projectId`    |
| CurseForge | `curseKey` or `CURSE_KEY`       | `curseforge.projectId` |
| Modifold   | `modifoldKey` or `MODIFOLD_KEY` | `modifold.projectId`   |

API key property names and environment variable names can be customized in the DSL using `apiKeyProp` and `apiKeyEnv`.

You can get your API from the following links:
- Modtale: https://modtale.net/dashboard/developer
- CurseForge: https://legacy.curseforge.com/account/api-tokens
- Modifold: https://modifold.com/settings/api

---

## Notes

- `key.properties` should contain API keys only.
- Project IDs belong in the Gradle DSL.
- Changelog paths are resolved relative to the root project directory.
- Dependency configuration is platform-specific because each hosting service supports different dependency metadata.
- Modifold dependency metadata is not currently supported.

---

## Platform Notes

### Modtale

- Uses `gameVersion` (defaults to `project.hytale_version`)
- Ensure your `hytale_version` matches a valid Modtale-supported version
- Accepts dynamic selectors like `2026.+` — see [Game Version](#game-version) for details
- Use `modtale.patchline` (`"release"` or `"pre-release"`) to scope dynamic resolution to a specific Hytale Maven repo

### CurseForge

- Uses numeric `gameVersionIds`
- Defaults to `[14284]` (Hytale Early Access)

### Modifold

- `gameVersions` and `loaders` must be arrays
- These are automatically JSON-encoded by the plugin

---

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.