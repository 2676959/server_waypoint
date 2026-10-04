plugins {
    id("java-library")
}

repositories {
    maven("https://libraries.minecraft.net")
    mavenCentral()
}

dependencies {
    api(project(":cross-server"))
    api("org.jetbrains:annotations:26.0.2")
    api("org.slf4j:slf4j-api:1.7.30")
    api("com.google.code.gson:gson:2.10.1")
    api("io.netty:netty-buffer:4.1.+")
    api("net.kyori:adventure-api:4.16.0")
    api("net.kyori:adventure-text-serializer-gson:4.16.0")
    api("com.mojang:brigadier:1.0.18")
    compileOnly("org.joml:joml:1.10.8")
    testImplementation("org.joml:joml:1.10.8")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.test {
    // Contract tests compile the production adapters against small platform API doubles.
    inputs.files(
        rootProject.file("paper/src/main/java/_959/server_waypoint/server/command/permission/PaperPermissionManager.java"),
        *listOf("fabric", "forge", "neoforge").map { loader ->
            rootProject.file("mods/src/main/java/_959/server_waypoint/$loader/permission/" +
                    when (loader) {
                        "fabric" -> "Fabric"
                        "forge" -> "Forge"
                        else -> "NeoForge"
                    } + "PermissionManager.java")
        }.toTypedArray()
    )
    useJUnitPlatform()
}

tasks.register<JavaExec>("generateFoliaLiveTestFixtures") {
    group = "verification"
    description = "Generates disposable Folia live-test waypoint fixtures and metadata."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("_959.server_waypoint.live.FoliaLiveTestFixtureTool")
    val outputDirectory = providers.gradleProperty("foliaLiveTestFixtureDir")
    doFirst {
        require(outputDirectory.isPresent) {
            "Set -PfoliaLiveTestFixtureDir to the disposable fixture directory"
        }
        args("generate", outputDirectory.get())
    }
    dependsOn(tasks.testClasses)
}

tasks.register<JavaExec>("verifyFoliaLiveTestControlFixture") {
    group = "verification"
    description = "Verifies the live Folia control list against its generated manifest."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("_959.server_waypoint.live.FoliaLiveTestFixtureTool")
    val fixtureDirectory = providers.gradleProperty("foliaLiveTestFixtureDir")
    val serverWaypointFile = providers.gradleProperty("foliaLiveTestServerWaypointFile")
    doFirst {
        require(fixtureDirectory.isPresent) {
            "Set -PfoliaLiveTestFixtureDir to the generated fixture directory"
        }
        require(serverWaypointFile.isPresent) {
            "Set -PfoliaLiveTestServerWaypointFile to the live server waypoint JSON file"
        }
        args("verify", fixtureDirectory.get(), serverWaypointFile.get())
    }
    dependsOn(tasks.testClasses)
}
