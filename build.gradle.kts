plugins {
    id("dev.architectury.loom")
    id("architectury-plugin")
    id("com.github.johnrengelman.shadow")
}

val minecraft = stonecutter.current.version
val loader = loom.platform.get().name.lowercase()
val mcVersionRange: String by project
val mod_id: String by project
val mod_name: String by project
val mod_version: String by project
val maven_group: String by project

group = maven_group

base {
    archivesName.set("$mod_id-$mod_version-$loader-mc$mcVersionRange")
}

stonecutter {
    constants.match(loader, "fabric", "neoforge")
}

architectury.common(stonecutter.tree.branches.mapNotNull {
    if (stonecutter.current.project !in it) null
    else property("loom.platform")?.toString()
})

sourceSets.main {
    if (loader == "fabric") {
        java {
            exclude("_959/server_waypoint/neoforge")
        }
        resources {
            exclude("META-INF")
            exclude("server_waypoint-neoforge.mixins.json")
        }
    }

    if (loader == "neoforge") {
        java {
            exclude("_959/server_waypoint/fabric")
        }
        resources {
            exclude("fabric.mod.json")
            exclude("server_waypoint-fabric.mixins.json")
        }
    }
}

repositories {
    exclusiveContent {
        forRepository {
            maven {
                name = "Modrinth"
                url = uri("https://api.modrinth.com/maven")
            }
        }
        filter {
            includeGroup("maven.modrinth")
        }
    }
    maven("https://maven.parchmentmc.org")
    maven("https://maven.neoforged.net/releases/")
    maven("https://maven.architectury.dev/")
}

dependencies {
    val yarn_build: String by project
    minecraft("com.mojang:minecraft:$minecraft")
    implementation(project(":common"))
    if (loader == "fabric") {
        val fabric_api: String by project
        val fabric_loader: String by project
        val xaeros_minimap_fabric: String by project
        val fabric_permissions_api: String by project
        modImplementation("net.fabricmc:fabric-loader:$fabric_loader")
        modImplementation("maven.modrinth:xaeros-minimap:$xaeros_minimap_fabric")
        mappings("net.fabricmc:yarn:$minecraft+build.$yarn_build:v2")
        modImplementation("net.fabricmc.fabric-api:fabric-api:$fabric_api")
        modImplementation("me.lucko:fabric-permissions-api:$fabric_permissions_api") {
            // exclude fabric-api brought by fabric-permission-api
            exclude("net.fabricmc.fabric-api")
        }
    }
    if (loader == "neoforge") {
        val neoforge_loader: String by project
        val neoforge_patch: String by project
        val xaeros_minimap_neoforge: String by project
//        val parchment_version: String by project
        "neoForge"("net.neoforged:neoforge:$neoforge_loader")
        modImplementation("maven.modrinth:xaeros-minimap:$xaeros_minimap_neoforge")
//        mappings(loom.layered {
//            officialMojangMappings()
//            parchment("org.parchmentmc.data:parchment-$parchment_version@zip")
//        })
        mappings(loom.layered {
            mappings("net.fabricmc:yarn:$minecraft+build.$yarn_build:v2")
            neoforge_patch.takeUnless { it.startsWith('[') }?.let {
                mappings("dev.architectury:yarn-mappings-patch-neoforge:$it")
            }
        })
    }
}

// Resource processing
tasks.processResources {
    inputs.property("id", mod_id)
    inputs.property("name", mod_name)
    inputs.property("version", mod_version)

    if (loader == "fabric") {
        val mcVersionFabric: String by project
        inputs.property("minecraft_dependency", mcVersionFabric)
        filesMatching("fabric.mod.json") {
            expand(mapOf(
                "id" to mod_id,
                "name" to mod_name,
                "version" to mod_version,
                "minecraft_dependency" to mcVersionFabric
            ))
        }
    }

    if (loader == "neoforge") {
        val mcVersionForge: String by project
        inputs.property("minecraft_dependency", mcVersionForge)
        filesMatching("META-INF/neoforge.mods.toml") {
            expand(mapOf(
                "id" to mod_id,
                "name" to mod_name,
                "version" to mod_version,
                "minecraft_dependency" to mcVersionForge,
            ))
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.shadowJar {
    dependencies {
        include(project(":common"))
        exclude("mappings/mappings.tiny")
    }
    archiveClassifier = "dev-shadow"
}

tasks.remapJar {
    input = tasks.shadowJar.get().archiveFile
    archiveClassifier = null
    dependsOn(tasks.shadowJar)
}

// License in jar
tasks.jar {
    from("LICENSE") {
        rename { "${it}_$mod_name" }
    }
}