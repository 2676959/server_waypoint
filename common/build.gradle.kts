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

        var content = outputFile.readText()
        content = content.replaceFirst(
            Regex("(MOD_ID = \")[^\"]*(\")"),
            "$1$modIdValue$2"
        )
        content = content.replaceFirst(
            Regex("(MOD_NAME = \")[^\"]*(\")"),
            "$1$modNameValue$2"
        )
        content = content.replaceFirst(
            Regex("(MOD_VERSION = \")[^\"]*(\")"),
            "$1$modVersionValue$2"
        )
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
    api("com.google.code.gson:gson:2.13.1")
    api("io.netty:netty-buffer:4.1.+")
    api("net.kyori:adventure-api:4.16.0")
    api("net.kyori:adventure-text-serializer-gson:4.16.0")
    api("com.mojang:brigadier:1.0.18")
}
