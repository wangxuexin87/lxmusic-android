pluginManagement {
    repositories {
        maven { url = uri("file:///home/hatch/local-maven-repo") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("file:///home/hatch/local-maven-repo") }
        google()
        mavenCentral()
    }
}
rootProject.name = "LXMusic"
include(":app")
