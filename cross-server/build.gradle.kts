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

dependencies {
    implementation("org.signal.forks:noise-java:${property("noise_version")}")
    api("org.jetbrains:annotations:26.0.2")
    api("org.slf4j:slf4j-api:1.7.30")
    api("com.google.code.gson:gson:2.10.1")
    api("io.netty:netty-buffer:4.1.+")
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
