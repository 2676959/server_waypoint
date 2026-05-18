plugins {
    id("net.neoforged.moddev")
    id("com.gradleup.shadow")
    id("me.modmuss50.mod-publish-plugin")
}

val minecraft = stonecutter.current.version
val loader = "neoforge"
val targetJavaVersion = if (stonecutter.eval(stonecutter.current.version, ">=1.20.5")) 21 else 17
val mcVersionRange: String by project
val mod_id: String by project
val mod_name: String by project
val mod_version: String by project
val maven_group: String by project

group = maven_group
version = "$mod_version-$loader-mc$mcVersionRange"

base {
    archivesName.set("$mod_id-$mod_version-$loader-mc$mcVersionRange")
}

val publishMinecraftVersions = expandMinecraftVersions(mcVersionRange)
val publishChangelog = providers.gradleProperty("mod_changelog")
    .orElse(providers.environmentVariable("MOD_CHANGELOG"))
    .orElse("Release $mod_version")
val publishDryRun = providers.gradleProperty("publish_dry_run")
    .map(String::toBoolean)
    .orElse(false)

stonecutter {
    constants.match(loader, "fabric", "neoforge")
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
    swaps["mouseScrolled_swap"] = when {
        eval(current.version, "<=1.20.1") -> "mouseScrolled($1, $2, $3)"
        else -> "mouseScrolled($1, $2, $3, $4)"
    }

    replacements.regex("gui_graphics_26", usesTwentySixApi) {
        replace("\\bGuiGraphics\\b", "GuiGraphicsExtractor")
        reverse("\\bGuiGraphicsExtractor\\b", "GuiGraphics")
    }
    replacements.string("gui_render_state_26", usesTwentySixApi) {
        replace("net.minecraft.client.gui.render.state.GuiElementRenderState", "net.minecraft.client.renderer.state.gui.GuiElementRenderState")
    }
    replacements.string("resource_location_import", usesResourceLocation) {
        replace("net.minecraft.resources.Identifier", "net.minecraft.resources.ResourceLocation")
    }
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

neoForge {
    val neoforge_loader: String by project
    version = neoforge_loader
    validateAccessTransformers = true

    runs {
        register("client") {
            client()
            gameDirectory = file("run")
        }
        register("server") {
            server()
            gameDirectory = file("run")
        }
    }

    mods {
        register(mod_id) {
            sourceSet(sourceSets["main"])
        }
    }
}

dependencies {
    implementation(project(":common"))
    addAdventureSerializerDependency()

    val xaeros_minimap_neoforge: String by project
    if (minecraft == "1.21.2") {
        compileOnly("maven.modrinth:xaeros-minimap:$xaeros_minimap_neoforge")
    } else {
        compileOnly("maven.modrinth:xaeros-minimap:$xaeros_minimap_neoforge")
        runtimeOnly("maven.modrinth:xaeros-minimap:$xaeros_minimap_neoforge")
    }
}

tasks.processResources {
    inputs.property("id", mod_id)
    inputs.property("name", mod_name)
    inputs.property("version", mod_version)
    inputs.property("java_version", targetJavaVersion)

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
    dependencies {
        include(project(":common"))
        include(dependency("net.kyori:.*"))
        exclude("mappings/*")
    }
    archiveClassifier.set("")
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

publishMods {
    file.set(tasks.shadowJar.flatMap { it.archiveFile })
    changelog.set(publishChangelog)
    type.set(STABLE)
    version.set(project.version.toString())
    displayName.set("$mod_name $mod_version NeoForge mc$mcVersionRange")
    modLoaders.add(loader)
    dryRun.set(publishDryRun)

    modrinth {
        accessToken.set(publishToken("MODRINTH_TOKEN", "MODRINTH_API_KEY"))
        projectId.set("server_waypoint")
        minecraftVersions.addAll(publishMinecraftVersions)
        optional("xaeros-minimap")
    }

    curseforge {
        accessToken.set(publishToken("CURSEFORGE_TOKEN", "CURSEFORGE_API_KEY"))
        projectId.set("1416929")
        projectSlug.set("server-waypoint")
        minecraftVersions.addAll(publishMinecraftVersions)
        clientRequired.set(true)
        serverRequired.set(true)
        javaVersions.add(JavaVersion.toVersion(targetJavaVersion))
        optional("xaeros-minimap")
    }
}

fun DependencyHandlerScope.addAdventureSerializerDependency() {
    val version = when (minecraft) {
        "1.20.2" -> "4.14.0"
        "1.20.4" -> "4.16.0"
        "1.20.6", "1.21" -> "4.17.0"
        "1.21.3" -> "4.20.0"
        "1.21.5" -> "4.24.0"
        else -> if (stonecutter.eval(stonecutter.current.version, ">=1.21.6")) "4.25.0" else "4.16.0"
    }
    implementation("net.kyori:adventure-text-serializer-gson:$version")
}

fun publishToken(vararg names: String) = names
    .map { providers.environmentVariable(it).orElse(providers.gradleProperty(it)) }
    .reduce { tokenProvider, nextTokenProvider -> tokenProvider.orElse(nextTokenProvider) }

fun expandMinecraftVersions(versionRange: String): List<String> {
    if (!versionRange.contains("-")) {
        return listOf(versionRange)
    }

    val (start, end) = versionRange.split("-", limit = 2)
    val startParts = start.split(".")
    val endParts = end.split(".")

    if (startParts.size == endParts.size && startParts.dropLast(1) == endParts.dropLast(1)) {
        val startPatch = startParts.last().toIntOrNull()
        val endPatch = endParts.last().toIntOrNull()
        if (startPatch != null && endPatch != null && startPatch <= endPatch) {
            val prefix = startParts.dropLast(1).joinToString(".")
            return (startPatch..endPatch).map { "$prefix.$it" }
        }
    }

    if (startParts.size + 1 == endParts.size && endParts.dropLast(1).joinToString(".") == start) {
        val endPatch = endParts.last().toIntOrNull()
        if (endPatch != null) {
            return listOf(start) + (1..endPatch).map { "$start.$it" }
        }
    }

    return listOf(start, end)
}
