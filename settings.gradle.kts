pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        maven("https://maven.parchmentmc.org") { name = "ParchmentMC" }
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.7.1"
}

stonecutter {
    kotlinController = true
    centralScript = "build.gradle.kts"

    // Subproject configuration
    create("mods") {
        fun mc(loader: String, vararg versions: String) {
            for (version in versions) vers("$version-$loader", version)
        }
        mc("fabric",
            "1.20.1", "1.20.2", "1.20.4", "1.20.6",
            "1.21", "1.21.2", "1.21.3", "1.21.5", "1.21.6", "1.21.9", "1.21.11",
            "26.1.2")
        mc("neoforge",
            "1.20.2", "1.20.4", "1.20.6",
            "1.21", "1.21.2", "1.21.3", "1.21.5", "1.21.6", "1.21.9", "1.21.11")

        mapBuilds { _, data ->
            val loader = data.project.substringAfterLast('-')
            when (data.project) {
                "1.20.2-neoforge" -> "neogradle.gradle.kts"
                "26.1.2-fabric" -> "fabric-unobfuscated.gradle.kts"
                else -> "$loader.gradle.kts"
            }
        }
    }

    create("paper") {
        vers("1.21-paper", "1.21")
    }
}

rootProject.name = "server_waypoint"
include("common")
include("mods")
include("paper")
