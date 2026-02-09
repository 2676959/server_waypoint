pluginManagement {
	repositories {
		mavenCentral()
		gradlePluginPortal()
		maven("https://maven.fabricmc.net/")
		maven("https://maven.architectury.dev")
		maven("https://maven.neoforged.net/releases/")
		maven("https://maven.kikugie.dev/snapshots")
	}
}

plugins {
	id("dev.kikugie.stonecutter") version "0.7.1"
}

stonecutter {
	kotlinController = true
	centralScript = "build.gradle.kts"

	// Subproject configuration
	create(rootProject) {
		fun mc(loader: String, vararg versions: String) {
			for (version in versions) vers("$version-$loader", version)
		}
		mc("fabric",
            "1.20.1", "1.20.2", "1.20.4", "1.20.6",
            "1.21", "1.21.2", "1.21.3", "1.21.5")
//		mc("neoforge", "1.21", "1.21.3", "1.21.5")
	}

	create("paper") {
		vers("1.21-paper", "1.21")
	}
}

rootProject.name = "server_waypoint"
include("common")
include("paper")