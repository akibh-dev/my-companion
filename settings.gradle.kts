pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Lets Gradle download a JDK for a toolchain it can't find (unit tests run on JDK 21, see app/build.gradle.kts).
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // NewPipeExtractor and its nanojson dependency are published on JitPack.
        maven("https://jitpack.io") {
            content { includeGroupByRegex("com\\.github\\..*") }
        }
    }
}

rootProject.name = "my-companion"
include(":app")

// Plan §2: fragile parts in their own modules; the AI models sit behind ai-api.
include(":core")
include(":ai-api")
include(":ai-models")
include(":adapters-messaging")
include(":feature-context")
include(":feature-autoreply")
include(":feature-voice")
include(":feature-vault")
include(":feature-capture")