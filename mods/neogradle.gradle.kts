plugins {
    id("java-library")
    id("net.neoforged.gradle.userdev")
    id("com.gradleup.shadow")
}

val minecraft = stonecutter.current.version
val loader = "neoforge"
val targetJavaVersion = 17
val mcVersionRange: String by project
val mod_id: String by project
val mod_name: String by project
val mod_version: String by project
val maven_group: String by project

group = maven_group

base {
    archivesName.set("$mod_id-$mod_version-$loader-mc$mcVersionRange")
}

val shadedDependencies by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

stonecutter {
    constants.match(loader, "fabric", "neoforge", "forge")
    val usesTwentySixApi = eval(current.version, ">=26")
    val usesResourceLocation = eval(current.version, "<1.21.11")

    swaps["render_widget_method_swap"] = if (usesTwentySixApi) "extractWidgetRenderState" else "renderWidget"
    swaps["render_method_swap"] = if (usesTwentySixApi) "extractRenderState" else "render"
    swaps["gui_text_method_swap"] = if (usesTwentySixApi) "text" else "drawString"
    swaps["gui_item_method_swap"] = if (usesTwentySixApi) "item" else "renderItem"
    swaps["gui_outline_method_swap"] = if (usesTwentySixApi) "outline" else "renderOutline"
    swaps["payload_s2c_registry_swap"] = if (usesTwentySixApi) "clientboundPlay" else "playS2C"
    swaps["payload_c2s_registry_swap"] = if (usesTwentySixApi) "serverboundPlay" else "playC2S"
    swaps["resource_location_type_swap"] = if (usesResourceLocation) "ResourceLocation" else "Identifier"
    swaps["mouseScrolled_swap"] = "mouseScrolled($1, $2, $3, $4)"

    replacements.regex("gui_graphics_26", usesTwentySixApi) { replace("\\bGuiGraphics\\b", "GuiGraphicsExtractor"); reverse("\\bGuiGraphicsExtractor\\b", "GuiGraphics") }
    replacements.string("gui_render_state_26", usesTwentySixApi) { replace("net.minecraft.client.gui.render.state.GuiElementRenderState", "net.minecraft.client.renderer.state.gui.GuiElementRenderState") }
    replacements.string("resource_location_import", usesResourceLocation) { replace("net.minecraft.resources.Identifier", "net.minecraft.resources.ResourceLocation") }
}

sourceSets.main {
    java {
        exclude("_959/server_waypoint/fabric")
        exclude("ServerWaypointNeoForge.java")
        exclude("ServerWaypointNeoForgeClient.java")
    }
    resources {
        exclude("fabric.mod.json")
        exclude("server_waypoint-fabric.mixins.json")
    }
}

repositories {
    mavenCentral()
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
}

dependencies {
    val neoforge_loader: String by project
    compileOnly("net.neoforged:neoforge:$neoforge_loader")
    implementation(project(":common"))
    add(shadedDependencies.name, project(":common"))

    val adventureSerializer = "net.kyori:adventure-text-serializer-gson:4.14.0"
    implementation(adventureSerializer)
    add(shadedDependencies.name, adventureSerializer)

    val xaeros_minimap_neoforge: String by project
    compileOnly("maven.modrinth:xaeros-minimap:$xaeros_minimap_neoforge")
    runtimeOnly("maven.modrinth:xaeros-minimap:$xaeros_minimap_neoforge")
}

runs {
    configureEach {
        workingDirectory = file("run")
    }
}

tasks.processResources {
    inputs.property("id", mod_id)
    inputs.property("name", mod_name)
    inputs.property("version", mod_version)
    inputs.property("java_version", targetJavaVersion)
    val resourcePackFormat = 18
    inputs.property("resource_pack_format", resourcePackFormat)

    filesMatching(listOf("*.mixins.json")) {
        expand("java_version" to targetJavaVersion)
    }

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

    doLast {
        destinationDir.resolve("assets/server_waypoint/icon.png")
            .copyTo(destinationDir.resolve("server_waypoint.png"), overwrite = true)

        destinationDir.resolve("pack.mcmeta").writeText("""
            {
              "pack": {
                "pack_format": $resourcePackFormat,
                "description": "$mod_name resources"
              }
            }
        """.trimIndent() + "\n")

        val metaInf = destinationDir.resolve("META-INF")
        val legacyMetadata = metaInf.resolve("neoforge.mods.toml").readText()
            .replace(Regex("""type\s*=\s*"required""""), "mandatory=true")
            .replace(Regex("""type\s*=\s*"optional""""), "mandatory=false")
        metaInf.resolve("mods.toml").writeText(legacyMetadata)
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
}

tasks.named("compileJava") {
    dependsOn("stonecutterGenerate")
}

tasks.named("processResources") {
    dependsOn("stonecutterGenerate")
}

tasks.jar {
    archiveClassifier.set("thin")
    from(rootProject.file("LICENSE")) {
        rename { "${it}_$mod_name" }
    }
}

tasks.shadowJar {
    configurations = listOf(shadedDependencies)
    addMultiReleaseAttribute.set(false)
    dependencies {
        include(project(":common"))
        include(dependency("net.kyori:.*"))
        exclude("mappings/*")
    }
    archiveClassifier.set("")
}

val prepareRunMods by tasks.registering(Copy::class) {
    group = "runs"
    from(tasks.shadowJar.map { it.archiveFile })
    into(layout.projectDirectory.dir("run/mods"))
    dependsOn(tasks.shadowJar)
}

tasks.configureEach {
    if (name == "runClient" || name == "runServer") {
        dependsOn(prepareRunMods)
    }
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

artifacts {
    archives(tasks.shadowJar)
}

tasks.register<Copy>("buildAndCollect") {
    group = "build"
    from(tasks.shadowJar.map { it.archiveFile })
    into(rootProject.layout.buildDirectory.file("libs/$mod_version"))
    dependsOn("build")
}
