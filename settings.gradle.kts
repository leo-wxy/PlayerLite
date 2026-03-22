pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PlayerLite"
include(":app")
include(":player")
include(":playback-client")
include(":playback-contract")
include(":playback-service")
include(":cache-core")
include(":network-core")
include(":user")
include(":feature-search")
include(":design-system")
include(":feature-detail-support")
include(":feature-playlist-detail")
include(":feature-album-detail")
include(":feature-artist-detail")
