import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import net.minecraftforge.gradle.shadow.net.minecraftforge.gradleutils.shared.ToolsExtension

plugins {
    id("net.minecraftforge.renamer")
    id("net.minecraftforge.gradle")
    id("com.gradleup.shadow")
}

val mcVersion = stonecutter.current.version
val loader = "forge"
val targetJavaVersion = 17
val mcVersionRange: String by project
val mod_id: String by project
val mod_name: String by project
val mod_version: String by project
val maven_group: String by project

group = maven_group
version = mod_version

base {
    archivesName.set("$mod_id-$mod_version-$loader-mc$mcVersionRange")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))
    }
}

extensions.configure<ToolsExtension>("fgtools") {
    configure("slimelauncher") {
        getJavaLauncher().set(javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))
        })
    }
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
        exclude("_959/server_waypoint/neoforge")
        exclude("ServerWaypointNeoForge.java")
        exclude("ServerWaypointNeoForgeClient.java")
    }
    resources {
        exclude("fabric.mod.json")
        exclude("META-INF/neoforge.mods.toml")
        exclude("server_waypoint-fabric.mixins.json")
    }
}

repositories {
    mavenCentral()
    maven("https://libraries.minecraft.net")
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
    maven("https://maven.minecraftforge.net/")
}

configure<net.minecraftforge.gradle.MinecraftExtensionForProject> {
    mappings("official", mcVersion)

    runs {
        create("client") {
            workingDir.set(project.layout.projectDirectory.dir("run"))
            systemProperty("forge.logging.markers", "REGISTRIES")
            systemProperty("forge.logging.console.level", "debug")
            args("-mixin.config=server_waypoint-common.mixins.json")
            mods {
                create(mod_id) {
                    source(sourceSets.main.get())
                }
            }
        }
        create("server") {
            workingDir.set(project.layout.projectDirectory.dir("run"))
            systemProperty("forge.logging.markers", "REGISTRIES")
            systemProperty("forge.logging.console.level", "debug")
            args("-mixin.config=server_waypoint-common.mixins.json")
            mods {
                create(mod_id) {
                    source(sourceSets.main.get())
                }
            }
        }
    }
}

val minecraftExtension = extensions.getByType<net.minecraftforge.gradle.MinecraftExtensionForProject>()
minecraftExtension.mavenizer(repositories)

dependencies {
    implementation(minecraftExtension.dependency("net.minecraftforge:forge:$mcVersion-${property("forge_loader")}").asProvider())

    implementation(project(":common"))
    add(shadedDependencies.name, project(":common"))
    addAdventureSerializerDependency()

    val xaeros_minimap_forge: String by project
    compileOnly("maven.modrinth:xaeros-minimap:$xaeros_minimap_forge")
    runtimeOnly("maven.modrinth:xaeros-minimap:$xaeros_minimap_forge")
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
    filesMatching("META-INF/mods.toml") {
        expand(mapOf(
            "id" to mod_id,
            "name" to mod_name,
            "version" to mod_version,
            "minecraft_dependency" to mcVersionForge,
            "forge_dependency" to "[47,)",
        ))
    }

    doLast {
        destinationDir.resolve("assets/server_waypoint/icon.png")
            .copyTo(destinationDir.resolve("server_waypoint.png"), overwrite = true)
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
    manifest {
        attributes(mapOf(
            "Specification-Title" to mod_id,
            "Specification-Vendor" to "2676959",
            "Specification-Version" to "1",
            "Implementation-Title" to mod_name,
            "Implementation-Version" to mod_version,
            "Implementation-Vendor" to "2676959",
            "MixinConfigs" to "server_waypoint-common.mixins.json",
        ))
    }
    from(rootProject.file("LICENSE")) {
        rename { "${it}_$mod_name" }
    }
}

tasks.named<ShadowJar>("shadowJar") {
    configurations = listOf(shadedDependencies)
    archiveClassifier.set("")
    dependencies {
        include(project(":common"))
        include(dependency("net.kyori:.*"))
        exclude("mappings/*")
    }
}

tasks.assemble {
    dependsOn(tasks.named("shadowJar"))
}

artifacts {
    archives(tasks.named("shadowJar"))
}

tasks.register<Copy>("buildAndCollect") {
    group = "build"
    from(tasks.named<ShadowJar>("shadowJar").map { it.archiveFile })
    into(rootProject.layout.buildDirectory.file("libs/$mod_version"))
    dependsOn("build")
}

fun DependencyHandlerScope.addAdventureSerializerDependency() {
    val dependencyNotation = "net.kyori:adventure-text-serializer-gson:4.14.0"
    implementation(dependencyNotation)
    add(shadedDependencies.name, dependencyNotation)
}
