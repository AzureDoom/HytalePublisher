# HytalePublisher

[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/com.azuredoom.hytalepublisher?label=Gradle%20Plugin%20Portal)](https://plugins.gradle.org/plugin/com.azuredoom.hytalepublisher)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Gradle](https://img.shields.io/badge/Gradle-plugin-02303A.svg?logo=gradle)](https://gradle.org/)

A Gradle plugin for publishing Hytale mods to Modtale, CurseForge, Modifold, Thunderstore, GitHub Releases, and custom Maven repositories.

Each platform is opt-in, and `publishAll` publishes to every enabled target.

## Features

- One Gradle DSL for release metadata and publishing targets
- Credentials from `key.properties` or environment variables
- Platform-specific dependencies and project identifiers
- Automatic builds before publishing
- GitHub Release and custom Maven publishing support
- Multi-module project support

## Installation

```groovy
plugins {
    id "com.azuredoom.hytalepublisher" version "1.1.11"
}
```

## Minimal configuration

```groovy
version = "1.0.0"
ext.hytale_version = "0.+"

hytalePublisher {
    releaseType = "release"
    changelogFile = "CHANGELOG.md"

    modtale {
        enabled = true
        projectId = "your-modtale-project-id"
        patchline = "release"
    }

    curseforge {
        enabled = true
        projectId = "123456"
    }

    modifold {
        enabled = true
        projectId = "your-modifold-project-slug"
        gameVersions = [project.hytale_version]
        loaders = ["Vanilla"]
    }
}
```

Add local credentials to an untracked `key.properties` file:

```properties
modTaleKey=your-modtale-api-key
curseKey=your-curseforge-api-token
modifoldKey=your-modifold-bearer-token
thunderstoreToken=your-thunderstore-service-account-token
githubToken=your-github-token
mavenUsername=your-repository-username
mavenPassword=your-repository-password
```

Environment variables are recommended for CI/CD.

## Publishing

Publish to every enabled platform:

```bash
./gradlew publishAll
```

Or publish to one platform:

```bash
./gradlew publishToModtale
./gradlew publishToCurseForge
./gradlew publishToModifold
./gradlew publishToThunderstore
./gradlew publishToGitHub
./gradlew publishToMaven
```

All publishing tasks depend on `build`.

## Documentation

Full configuration, credentials, platform details, CI examples, and release workflows are maintained in the [HytalePublisher Wiki](https://github.com/AzureDoom/HytalePublisher/wiki).

- [Installation](https://github.com/AzureDoom/HytalePublisher/wiki/Installation)
- [Credentials](https://github.com/AzureDoom/HytalePublisher/wiki/Credentials)
- [Configuration](https://github.com/AzureDoom/HytalePublisher/wiki/Configuration)
- [Minimal Configuration](https://github.com/AzureDoom/HytalePublisher/wiki/Minimal-Configuration)
- [Publishing a Release](https://github.com/AzureDoom/HytalePublisher/wiki/Publishing-a-Release)
- [CI/CD Example](https://github.com/AzureDoom/HytalePublisher/wiki/CI-CD-Example)
- [Multi-Module Projects](https://github.com/AzureDoom/HytalePublisher/wiki/Multi-Module-Projects)
- [Tasks](https://github.com/AzureDoom/HytalePublisher/wiki/Tasks)
- [Credential and Project ID Reference](https://github.com/AzureDoom/HytalePublisher/wiki/Credential-and-Project-ID-Reference)
- [Platform Notes](https://github.com/AzureDoom/HytalePublisher/wiki/Platform-Notes)
- [Thunderstore](https://github.com/AzureDoom/HytalePublisher/wiki/Thunderstore)

## Support

Report bugs or request features through [GitHub Issues](https://github.com/AzureDoom/HytalePublisher/issues).

## License

Licensed under the [MIT License](LICENSE).
