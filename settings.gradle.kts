// Only Google's and Maven Central's official repositories are trusted here.
// No JitPack, no personal Maven hosts: every dependency is one an auditor can
// look up by coordinate, and nothing is fetched from an individual's server.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Zephyr"
include(":app")
