plugins {
    java
    id("com.gradleup.shadow")
}

group = property("maven_group") as String
version = property("mod_version") as String
base.archivesName.set("server_waypoint-${version}-velocity")

repositories {
    maven("https://libraries.minecraft.net")
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    implementation(project(":proxy-common"))
    compileOnly("com.velocitypowered:velocity-api:${property("velocity_api_version")}")
    annotationProcessor("com.velocitypowered:velocity-api:${property("velocity_api_version")}")
}

java {
    // Match the current Velocity runtime while restricting source to Java 17 language features.
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.withType<Jar>().configureEach {
    archiveVersion.set("")
}

tasks.jar {
    archiveClassifier.set("unshaded")
}

tasks.shadowJar {
    relocate("com.southernstorm.noise", "_959.server_waypoint.internal.noisekk")
    dependencies {
        include(dependency("org.signal.forks:noise-java:.*"))
    }
    archiveClassifier.set("")
    dependencies {
        include(project(":common"))
        include(project(":proxy-common"))
    }
    from(rootProject.file("LICENSE")) {
        rename { "LICENSE-server-waypoint" }
    }
}

apply(from = rootProject.file("gradle/noise-packaging.gradle.kts"))

tasks.assemble {
    dependsOn(tasks.shadowJar)
}
