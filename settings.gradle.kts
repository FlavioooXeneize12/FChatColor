pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        mavenCentral()
        maven("https://hub.spigotmc.org/nexus/content/repositories/releases/") { name = "spigotmc-releases" }
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") { name = "spigotmc-snapshots" }
        maven("https://repo.papermc.io/repository/maven-public/") { name = "papermc" }
        maven("https://repo.extendedclip.com/releases/") { name = "extendedclip" }
    }
}

rootProject.name = "FChatColor-Parent"
include("fchatcolor-api", "fchatcolor-legacy", "fchatcolor-modern", "fchatcolor-plugin")
