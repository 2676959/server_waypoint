plugins {
    `java-library`
}

repositories {
    maven("https://libraries.minecraft.net")
}

dependencies {
    api(project(":cross-server"))
    // Contract tests drive real backend transports; TcpTransportTest also uses Noise directly.
    testImplementation(project(":common"))
    testImplementation("org.signal.forks:noise-java:${property("noise_version")}")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

tasks.test {
    useJUnitPlatform()
}
