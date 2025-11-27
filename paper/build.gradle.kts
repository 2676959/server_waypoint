plugins {
    id("java")
    id("xyz.jpenilla.run-paper") version "2.3.1"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.18"
    id("com.github.johnrengelman.shadow")
}

group = "_959.server_waypoint"
val mod_version: String by project
val mod_id: String by project
val mcVersion : String by project
val mcVersionRange : String by project

base {
    archivesName.set("$mod_id-$mod_version-paper-mc$mcVersionRange")
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://oss.sonatype.org/content/groups/public/")
}

dependencies {
    val paperApiVersion : String by project
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
    paperweight.paperDevBundle(paperApiVersion)
    implementation(project(":common"))
}

tasks {
    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion(mcVersion)
    }
}

tasks.jar {
    enabled = false
}

tasks.shadowJar {
    dependencies {
        include(project(":common"))
    }
    archiveClassifier.set("")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

artifacts {
    archives(tasks.shadowJar)
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
}


