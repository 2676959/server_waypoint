plugins {
    id("net.fabricmc.fabric-loom")
    id("com.gradleup.shadow")
    id("me.modmuss50.mod-publish-plugin")
}

val minecraft = stonecutter.current.version
val loader = "fabric"
val targetJavaVersion = 25
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
    swaps["mouseScrolled_swap"] = "mouseScrolled($1, $2, $3, $4)"

    replacements.regex("gui_graphics_26", usesTwentySixApi) { replace("\\bGuiGraphics\\b", "GuiGraphicsExtractor"); reverse("\\bGuiGraphicsExtractor\\b", "GuiGraphics") }
    replacements.string("gui_render_state_26", usesTwentySixApi) { replace("net.minecraft.client.gui.render.state.GuiElementRenderState", "net.minecraft.client.renderer.state.gui.GuiElementRenderState") }
    replacements.string("resource_location_import", usesResourceLocation) { replace("net.minecraft.resources.Identifier", "net.minecraft.resources.ResourceLocation") }
    replacements.string("fabric_key_mapping_import_26", usesTwentySixApi) { replace("net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper", "net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper") }
    replacements.string("fabric_key_mapping_call_26", usesTwentySixApi) { replace("KeyBindingHelper.registerKeyBinding", "KeyMappingHelper.registerKeyMapping") }
}

sourceSets.main {
    java {
        exclude("_959/server_waypoint/neoforge")
        exclude("ServerWaypointNeoForge.java")
        exclude("ServerWaypointNeoForgeClient.java")
    }
    resources {
        exclude("META-INF")
        exclude("server_waypoint-neoforge.mixins.json")
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
    maven {
        name = "Xaero's Maven"
        url = uri("https://chocolateminecraft.com/maven")
        content {
            includeGroup("xaero.lib")
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraft")

    implementation(project(":common"))
    addAdventureSerializerDependency()

    val fabric_api: String by project
    val fabric_loader: String by project
    val fabric_permissions_api: String by project
    val xaeros_minimap_fabric: String by project

    implementation("net.fabricmc:fabric-loader:$fabric_loader")
    implementation("net.fabricmc.fabric-api:fabric-api:$fabric_api")
    implementation("me.lucko:fabric-permissions-api:$fabric_permissions_api") {
        exclude("net.fabricmc.fabric-api")
    }

    if (project.hasProperty("xaerolib_fabric")) {
        implementation("xaero.lib:xaerolib-fabric-$minecraft:${property("xaerolib_fabric")}") {
            exclude("net.fabricmc")
            exclude("net.fabricmc.fabric-api")
        }
    }

    implementation("maven.modrinth:xaeros-minimap:$xaeros_minimap_fabric")
}

tasks.processResources {
    inputs.property("id", mod_id)
    inputs.property("name", mod_name)
    inputs.property("version", mod_version)
    inputs.property("java_version", targetJavaVersion)

    filesMatching(listOf("*.mixins.json")) {
        expand("java_version" to targetJavaVersion)
    }

    val fabric_loader: String by project
    val mcVersionFabric: String by project
    inputs.property("fabricloader_dependency", fabric_loader)
    inputs.property("minecraft_dependency", mcVersionFabric)
    filesMatching("fabric.mod.json") {
        expand(mapOf(
            "id" to mod_id,
            "name" to mod_name,
            "version" to mod_version,
            "fabricloader_dependency" to ">=$fabric_loader",
            "minecraft_dependency" to mcVersionFabric,
            "java_version" to targetJavaVersion,
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
    from(rootProject.file("LICENSE")) {
        rename { "${it}_$mod_name" }
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

publishMods {
    file.set(tasks.shadowJar.flatMap { it.archiveFile })
    changelog.set(publishChangelog)
    type.set(STABLE)
    version.set(project.version.toString())
    displayName.set("$mod_name $mod_version Fabric mc$mcVersionRange")
    modLoaders.add(loader)
    dryRun.set(publishDryRun)

    modrinth {
        accessToken.set(publishToken("MODRINTH_TOKEN", "MODRINTH_API_KEY"))
        projectId.set("server_waypoint")
        minecraftVersions.addAll(publishMinecraftVersions)
        requires("fabric-api")
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
        requires("fabric-api")
        optional("xaeros-minimap")
    }
}

fun DependencyHandlerScope.addAdventureSerializerDependency() {
    implementation("net.kyori:adventure-text-serializer-gson:4.25.0")
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
