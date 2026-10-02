import java.security.MessageDigest

plugins {
    java
    id("com.gradleup.shadow") version "9.4.1"
}

repositories {
    mavenCentral()
}

val candidateCommit = "4de5aefbf2bb19bf2efcfbe97d3d13231e693488"
val candidateSha256 = "e31e460651048e2f53a28d88da9ba417dbb9ba3da45bebd3335e5357eaaa4205"
val candidateArchive = layout.buildDirectory.file("downloads/java-noise.zip")

val downloadCandidate = tasks.register("downloadCandidate") {
    inputs.property("commit", candidateCommit)
    inputs.property("sha256", candidateSha256)
    outputs.file(candidateArchive)
    // Verify the pinned bytes even if a previous invocation left a cached archive.
    outputs.upToDateWhen { false }
    doLast {
        val archive = candidateArchive.get().asFile
        if (!archive.exists()) {
            archive.parentFile.mkdirs()
            val connection = uri("https://codeload.github.com/jchambers/java-noise/zip/$candidateCommit")
                .toURL().openConnection()
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.getInputStream().use { input ->
                archive.outputStream().use { output -> input.copyTo(output) }
            }
        }
        val actual = MessageDigest.getInstance("SHA-256").digest(archive.readBytes())
            .joinToString("") { "%02x".format(it) }
        check(actual == candidateSha256) { "Candidate archive SHA-256 mismatch; remove the cached archive and retry" }
    }
}

tasks.register<Sync>("extractCandidate") {
    dependsOn(downloadCandidate)
    from(candidateArchive.map { zipTree(it) })
    into(layout.buildDirectory.dir("upstream"))
}

dependencies {
    implementation(project(":candidate"))
    testImplementation("com.google.code.gson:gson:2.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.signal.forks:noise-java:0.1.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

tasks.shadowJar {
    archiveClassifier.set("relocated")
    relocate("com.eatthepath.noise", "_959.server_waypoint.internal.noise")
    relocate("javax.annotation", "_959.server_waypoint.internal.noiseannotations")
    manifest.attributes["Main-Class"] = "_959.server_waypoint.noisespike.NoisePeer"
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.test {
    dependsOn(tasks.shadowJar)
    useJUnitPlatform()
    maxParallelForks = 1
    systemProperty("spike.classpath", sourceSets.main.get().runtimeClasspath.asPath)
    systemProperty("spike.relocatedJar", tasks.shadowJar.get().archiveFile.get().asFile.absolutePath)
    systemProperty("spike.vectorFile", layout.buildDirectory.file(
        "upstream/java-noise-$candidateCommit/src/test/resources/com/eatthepath/noise/cacophony-test-vectors.json"
    ).get().asFile.absolutePath)
    testLogging {
        events("passed", "failed", "skipped")
    }
}
