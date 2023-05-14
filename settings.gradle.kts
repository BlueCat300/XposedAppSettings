pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven (url = "https://api.xposed.info/")
        maven (url = "https://s01.oss.sonatype.org/content/repositories/releases")
        maven (url = "https://www.jitpack.io")
        google()
        mavenCentral()
    }
}

rootProject.name = "Xposed App Settings"
include (":app")
