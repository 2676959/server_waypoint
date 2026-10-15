plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    api("com.google.code.findbugs:jsr305:3.0.2")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

sourceSets.main {
    java.setSrcDirs(listOf(rootProject.layout.buildDirectory.dir(
        "upstream/java-noise-4de5aefbf2bb19bf2efcfbe97d3d13231e693488/src/main/java"
    )))
}

tasks.compileJava {
    dependsOn(rootProject.tasks.named("extractCandidate"))
    options.release.set(17)
}

tasks.jar {
    from(rootProject.layout.buildDirectory.file(
        "upstream/java-noise-4de5aefbf2bb19bf2efcfbe97d3d13231e693488/LICENSE"
    )) {
        into("META-INF")
        rename { "LICENSE-java-noise" }
    }
}
