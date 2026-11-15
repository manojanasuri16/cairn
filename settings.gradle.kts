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

rootProject.name = "Cairn"

include(
    ":app",
    ":core:model",
    ":core:database",
    ":core:datastore",
    ":core:designsystem",
    ":core:common",
    ":data",
    ":source:healthconnect",
    ":source:corosmcp",
    ":sync",
    ":widget",
)
