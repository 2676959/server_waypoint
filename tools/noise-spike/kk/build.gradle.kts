import java.security.MessageDigest

plugins {
    java
    id("com.gradleup.shadow")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.signal.forks:noise-java:0.1.1")
    testImplementation("com.google.code.gson:gson:2.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

val verifyCandidate = tasks.register("verifyCandidate") {
    val runtime = configurations.runtimeClasspath
    inputs.files(runtime)
    doLast {
        val artifacts = runtime.get().resolvedConfiguration.resolvedArtifacts
        check(artifacts.size == 1) { "Unexpected KK runtime dependencies" }
        val artifact = artifacts.single()
        check(artifact.moduleVersion.id.toString() == "org.signal.forks:noise-java:0.1.1")
        val hash = MessageDigest.getInstance("SHA-256").digest(artifact.file.readBytes())
            .joinToString("") { "%02x".format(it) }
        check(hash == "2bbc531e5e31b3151269dbb7596548e3c884ded217ab6312c2d87591bfea543b") {
            "KK candidate JAR SHA-256 mismatch"
        }
    }
}

tasks.compileJava {
    dependsOn(verifyCandidate)
}

tasks.shadowJar {
    archiveBaseName.set("server-waypoint-noise-kk-spike")
    archiveClassifier.set("relocated")
    relocate("com.southernstorm.noise", "_959.server_waypoint.internal.noisekk")
    manifest.attributes["Main-Class"] = "_959.server_waypoint.noisespike.kk.KkPeer"
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.test {
    dependsOn(tasks.shadowJar, rootProject.tasks.named("extractCandidate"))
    useJUnitPlatform()
    maxParallelForks = 1
    systemProperty("spike.classpath", sourceSets.main.get().runtimeClasspath.asPath)
    systemProperty("spike.relocatedJar", tasks.shadowJar.get().archiveFile.get().asFile.absolutePath)
    systemProperty("spike.vectorFile", rootProject.layout.buildDirectory.file(
        "upstream/java-noise-4de5aefbf2bb19bf2efcfbe97d3d13231e693488/src/test/resources/com/eatthepath/noise/cacophony-test-vectors.json"
    ).get().asFile.absolutePath)
    testLogging {
        events("passed", "failed", "skipped")
    }
}
