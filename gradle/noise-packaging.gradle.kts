import java.security.MessageDigest

// Shared by every final platform packaging route. The runtime dependency is owned by common.
val noiseVerification = configurations.create("noiseDependencyVerification") {
    isCanBeConsumed = false
    isTransitive = false
}
dependencies.add(noiseVerification.name, "org.signal.forks:noise-java:${property("noise_version")}")

val verifyNoiseDependency = tasks.register("verifyNoiseDependency") {
    group = "verification"
    description = "Verifies the exact reviewed Noise runtime bytes before platform packaging."
    inputs.files(noiseVerification)
    doLast {
        val artifact = noiseVerification.resolvedConfiguration.resolvedArtifacts.single()
        check(artifact.moduleVersion.id.toString() == "org.signal.forks:noise-java:0.1.1")
        val hash = MessageDigest.getInstance("SHA-256").digest(artifact.file.readBytes())
            .joinToString("") { "%02x".format(it) }
        check(hash == "2bbc531e5e31b3151269dbb7596548e3c884ded217ab6312c2d87591bfea543b") {
            "Noise dependency differs from the step-2 reviewed artifact"
        }
    }
}

tasks.named<Jar>("shadowJar") {
    dependsOn(verifyNoiseDependency)
    from(rootProject.file("gradle/licenses/noise-java.txt")) {
        into("META-INF")
        rename { "LICENSE-noise-java" }
    }
}
