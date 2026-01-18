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
        maven { url = uri("https://mudita.jfrog.io/artifactory/mmd-release") }
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "CalmMusic"
include(":app")
