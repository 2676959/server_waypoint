plugins {
    id("net.fabricmc.fabric-loom-remap")
    id("com.gradleup.shadow")
}

val minecraft = stonecutter.current.version
val loader = "fabric"
val targetJavaVersion = if (stonecutter.eval(stonecutter.current.version, ">=1.20.5")) 21 else 17
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
    constants.match(loader, "fabric", "neoforge", "forge")
    val usesTwentySixApi = eval(current.version, ">=26")
    val usesResourceLocation = eval(current.version, "<1.21.11")

    swaps["render_widget_method_swap"] = if (usesTwentySixApi) "extractWidgetRenderState" else "renderWidget"
    swaps["render_method_swap"] = if (usesTwentySixApi) "extractRenderState" else "render"
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
    replacements.string("fabric_key_mapping_import_26", usesTwentySixApi) {
        replace("net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper", "net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper")
    }
    replacements.string("fabric_key_mapping_call_26", usesTwentySixApi) {
        replace("KeyBindingHelper.registerKeyBinding", "KeyMappingHelper.registerKeyMapping")
    }
}

sourceSets.main {
    java {
        exclude("_959/server_waypoint/forge")
        exclude("_959/server_waypoint/neoforge")
    }
    resources {
        exclude("META-INF")
        exclude("pack.mcmeta")
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
    mappings(loom.officialMojangMappings())

    implementation(project(":common"))
    addAdventureSerializerDependency()

    val fabric_api: String by project
    val fabric_loader: String by project
    val fabric_permissions_api: String by project
    val xaeros_minimap_fabric: String by project

    modImplementation("net.fabricmc:fabric-loader:$fabric_loader")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabric_api")
    modImplementation("me.lucko:fabric-permissions-api:$fabric_permissions_api") {
        exclude("net.fabricmc.fabric-api")
    }

    if (project.hasProperty("xaerolib_fabric")) {
        modImplementation("xaero.lib:xaerolib-fabric-$minecraft:${property("xaerolib_fabric")}") {
            exclude("net.fabricmc")
            exclude("net.fabricmc.fabric-api")
        }
    }

    if (minecraft == "1.21.2") {
        modCompileOnly("maven.modrinth:xaeros-minimap:$xaeros_minimap_fabric")
    } else {
        modImplementation("maven.modrinth:xaeros-minimap:$xaeros_minimap_fabric")
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

    val mcVersionFabric: String by project
    inputs.property("minecraft_dependency", mcVersionFabric)
    filesMatching("fabric.mod.json") {
        expand(mapOf(
            "id" to mod_id,
            "name" to mod_name,
            "version" to mod_version,
            "fabricloader_dependency" to ">=0.16.10",
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

tasks.shadowJar {
    dependencies {
        include(project(":common"))
        include(dependency("net.kyori:.*"))
        exclude("mappings/*")
    }
    archiveClassifier.set("dev-shadow")
}

tasks.remapJar {
    inputFile.set(tasks.shadowJar.flatMap { it.archiveFile })
    archiveClassifier.set("")
    dependsOn(tasks.shadowJar)
}

tasks.jar {
    from(rootProject.file("LICENSE")) {
        rename { "${it}_$mod_name" }
    }
}

tasks.register<Copy>("buildAndCollect") {
    group = "build"
    from(tasks.remapJar.map { it.archiveFile })
    into(rootProject.layout.buildDirectory.file("libs/$mod_version"))
    dependsOn("build")
}

fun DependencyHandlerScope.addAdventureSerializerDependency() {
    val version = when (minecraft) {
        "1.20.1", "1.20.2" -> "4.14.0"
        "1.20.4" -> "4.16.0"
        "1.20.6", "1.21" -> "4.17.0"
        "1.21.3" -> "4.20.0"
        "1.21.5" -> "4.24.0"
        else -> if (stonecutter.eval(stonecutter.current.version, ">=1.21.6")) "4.25.0" else "4.16.0"
    }
    implementation("net.kyori:adventure-text-serializer-gson:$version")
}
