pluginManagement {
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

rootProject.name = "GBCast"
include(":gbcast")
project(":gbcast").projectDir = file("flycast")
// Shared GBFrame UI (git submodule). The library's own minSdk is 28.
include(":gbframe")
project(":gbframe").projectDir = file("../../libs/gbframe/gbframe")
