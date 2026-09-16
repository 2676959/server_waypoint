import java.util.Properties
import java.io.FileInputStream

plugins {
    id("java-library")
}

tasks.register("updateModInfo") {
    group = "build"
    description = "Updates ModInfo.java from gradle.properties"
    val outputFile = file("src/main/java/_959/server_waypoint/ModInfo.java")

    inputs.file(rootProject.file("gradle.properties"))
    outputs.file(outputFile)

    doLast {
        val props = Properties()
        FileInputStream(rootProject.file("gradle.properties")).use { props.load(it) }
        val modIdValue = props.getProperty("mod_id")
        val modNameValue = props.getProperty("mod_name")
        val modVersionValue = props.getProperty("mod_version")
        val downloadUrl = props.getProperty("download_url")

        val content = """
            package _959.server_waypoint;

            /**
             * properties from gradle.properties, values are placeholders
             * */
            public final class ModInfo {
                public static final String MOD_ID = "$modIdValue";
                public static final String MOD_NAME = "$modNameValue";
                public static final String MOD_VERSION = "$modVersionValue";
                public static final String DOWNLOAD_URL = "$downloadUrl";
            }
        """.trimIndent()
        outputFile.writeText(content)
    }
}

tasks.named("compileJava") {
    dependsOn(tasks.named("updateModInfo"))
}

repositories {
    maven("https://libraries.minecraft.net")
    mavenCentral()
}

dependencies {
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
