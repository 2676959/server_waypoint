import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import net.minecraftforge.gradle.shadow.net.minecraftforge.gradleutils.shared.ToolsExtension
import org.gradle.jvm.tasks.Jar
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.GZIPInputStream

plugins {
    id("net.minecraftforge.renamer")
    id("net.minecraftforge.gradle")
    id("com.gradleup.shadow")
}

val minecraftVersion = stonecutter.current.version
val loader = "forge"
val targetJavaVersion = when {
    stonecutter.eval(minecraftVersion, ">=26") -> 25
    stonecutter.eval(minecraftVersion, ">=1.20.5") -> 21
    else -> 17
}

val mcVersionRange: String by project
val mod_id: String by project
val mod_name: String by project
val mod_version: String by project
val maven_group: String by project
val forge_loader: String by project
val mixinConfig = "server_waypoint-common.mixins.json"
val mixinRefmap = "server_waypoint-common.refmap.json"
val needsSrgReobf = stonecutter.eval(minecraftVersion, "<1.20.6")

evaluationDependsOn(":common")
val commonMainSourceSet = project(":common")
    .extensions
    .getByType(org.gradle.api.tasks.SourceSetContainer::class.java)
    .named("main")

group = maven_group
version = mod_version

base {
    archivesName.set("$mod_id-$mod_version-$loader-mc$mcVersionRange")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))
    }
    sourceCompatibility = JavaVersion.toVersion(targetJavaVersion)
    targetCompatibility = JavaVersion.toVersion(targetJavaVersion)
}

extensions.configure<ToolsExtension>("fgtools") {
    configure("slimelauncher") {
        getJavaLauncher().set(javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))
        })
    }
}

extensions.configure<net.minecraftforge.renamer.gradle.shadow.net.minecraftforge.gradleutils.shared.ToolsExtension>("renamerTools") {
    configure("classes") {
        getJavaLauncher().set(javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))
        })
    }
}

val shadedDependencies by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val unpackedMixinMappings = layout.buildDirectory.file("mixin/official-to-srg.tsrg")
val unpackMixinMappings = if (needsSrgReobf) {
    val mixinMappingsArchive = providers.provider {
        rootProject.fileTree(".gradle/mavenizer/repo/net/minecraft/mappings_official") {
            include("$minecraftVersion-*/mappings_official-$minecraftVersion-*-map2srg.tsrg.gz")
        }.singleFile
    }

    tasks.register("unpackMixinMappings") {
        inputs.file(mixinMappingsArchive)
        outputs.file(unpackedMixinMappings)

        doLast {
            val output = unpackedMixinMappings.get().asFile
            output.parentFile.mkdirs()
            GZIPInputStream(mixinMappingsArchive.get().inputStream()).use { input ->
                Files.copy(input, output.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
} else {
    null
}

stonecutter {
    constants.match(loader, "fabric", "neoforge", "forge")
    val usesTwentySixApi = eval(current.version, ">=26")
    val usesResourceLocation = eval(current.version, "<1.21.11")

    swaps["render_widget_method_swap"] = if (usesTwentySixApi) "extractWidgetRenderState" else "renderWidget"
    swaps["render_method_swap"] = if (usesTwentySixApi) "extractRenderState" else "render"
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
    }
    resources {
        srcDir("src/generated/resources")
        exclude("fabric.mod.json")
        exclude("META-INF/neoforge.mods.toml")
        exclude("server_waypoint-fabric.mixins.json")
    }
}

sourceSets.configureEach {
    val outputDir = layout.buildDirectory.dir("sourceSets/$name")
    output.setResourcesDir(outputDir.get().asFile)
    java.destinationDirectory.set(outputDir)
}

repositories {
    clear()
    maven("https://libraries.minecraft.net") {
        name = "MinecraftLibraries"
    }
    mavenCentral()
    maven("https://repo.spongepowered.org/repository/maven-public/")
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

val minecraftExtension = extensions.getByType<net.minecraftforge.gradle.MinecraftExtensionForProject>()

minecraft {
    mappings("official", minecraftVersion)

    if (stonecutter.eval(minecraftVersion, ">=1.20.6")) {
        javaClass.methods
            .singleOrNull { it.name == "setReobf" && it.parameterTypes.contentEquals(arrayOf(Boolean::class.javaPrimitiveType)) }
            ?.invoke(this, false)
    }

    runs {
        configureEach {
            workingDir.set(project.layout.projectDirectory.dir("run"))
            systemProperty("forge.logging.markers", "REGISTRIES")
            systemProperty("forge.logging.console.level", "debug")
            args("-mixin.config=$mixinConfig")
            mods {
                create(mod_id) {
                    source(sourceSets.main.get())
                    source(commonMainSourceSet.get())
                }
            }
        }

        create("client") {
            systemProperty("forge.enabledGameTestNamespaces", mod_id)
        }

        create("server") {
            systemProperty("forge.enabledGameTestNamespaces", mod_id)
            args("--nogui")
        }

        create("gameTestServer") {
            systemProperty("forge.enabledGameTestNamespaces", mod_id)
        }

        create("data") {
            workingDir.set(project.layout.projectDirectory.dir("run-data"))
            args(
                "--mod", mod_id,
                "--all",
                "--output", file("src/generated/resources/"),
                "--existing", file("src/main/resources/")
            )
        }
    }
}

minecraftExtension.mavenizer(repositories)

dependencies {
    implementation(minecraftExtension.dependency("net.minecraftforge:forge:$minecraftVersion-$forge_loader").asProvider())
    annotationProcessor("org.spongepowered:mixin:0.8.5:processor")

    implementation(project(":common"))
    add(shadedDependencies.name, project(":common"))
    addAdventureSerializerDependency()

    val xaeros_minimap_forge: String by project
    compileOnly("maven.modrinth:xaeros-minimap:$xaeros_minimap_forge")
}

tasks.processResources {
    val mcVersionForge: String by project
    val loaderVersion: String by project
    val replaceProperties = mapOf(
        "id" to mod_id,
        "name" to mod_name,
        "version" to mod_version,
        "java_version" to targetJavaVersion,
        "minecraft_dependency" to mcVersionForge,
        "forge_dependency" to loaderVersion,
    )

    inputs.properties(replaceProperties)

    filesMatching(listOf("*.mixins.json")) {
        expand("java_version" to targetJavaVersion)
    }

    filesMatching("META-INF/mods.toml") {
        expand(replaceProperties)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(targetJavaVersion)
    val mixinCompilerArgs = mutableListOf(
        "-Xlint:deprecation",
        "-Xlint:unchecked",
        "-AoutRefMapFile=${layout.buildDirectory.file("sourceSets/main/$mixinRefmap").get().asFile.absolutePath}",
        "-AMSG_NO_OBFDATA_FOR_TARGET=warning",
    )
    if (needsSrgReobf) {
        dependsOn(unpackMixinMappings!!)
        mixinCompilerArgs.addAll(listOf(
            "-AreobfTsrgFile=${unpackedMixinMappings.get().asFile.absolutePath}",
            "-AoutTsrgFile=${layout.buildDirectory.file("tmp/compileJava/${mixinRefmap.removeSuffix(".refmap.json")}-mixins.tsrg").get().asFile.absolutePath}",
            "-AmappingTypes=tsrg",
            "-AdefaultObfuscationEnv=searge",
        ))
    }
    options.compilerArgs.addAll(mixinCompilerArgs)
}

tasks.named("compileJava") {
    dependsOn("stonecutterGenerate")
}

tasks.named("processResources") {
    dependsOn("stonecutterGenerate")
}

tasks.withType<Jar>().configureEach {
    archiveVersion.set("")
    manifest {
        attributes(mapOf(
            "Specification-Title" to mod_id,
            "Specification-Vendor" to "2676959",
            "Specification-Version" to "1",
            "Implementation-Title" to mod_name,
            "Implementation-Version" to mod_version,
            "Implementation-Vendor" to "2676959",
            "MixinConfigs" to mixinConfig,
        ))
    }
    from(rootProject.file("LICENSE")) {
        rename { "${it}_$mod_name" }
    }
}

tasks.jar {
    archiveClassifier.set("thin")
}

tasks.named<ShadowJar>("shadowJar") {
    configurations = listOf(shadedDependencies)
    archiveClassifier.set(if (needsSrgReobf) "dev-shadow" else "")
    addMultiReleaseAttribute.set(false)
    exclude("META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.SF", "META-INF/MANIFEST.MF", "mappings/**")
    dependencies {
        include(project(":common"))
        include(dependency("net.kyori:.*"))
    }
}

val reobfShadowJar = if (needsSrgReobf) {
    extensions
        .getByType(net.minecraftforge.renamer.gradle.RenamerExtension::class.java)
        .classes("reobfShadowJar", tasks.named<ShadowJar>("shadowJar")) {
            archiveClassifier.set("")
            output.set(layout.buildDirectory.file("libs/${base.archivesName.get()}.jar"))
            dependsOn(unpackMixinMappings!!)
            setMappings(files(unpackedMixinMappings))
        }
} else {
    null
}

tasks.assemble {
    dependsOn(reobfShadowJar ?: tasks.shadowJar)
}

artifacts {
    archives(reobfShadowJar ?: tasks.shadowJar)
}

tasks.register<Copy>("buildAndCollect") {
    group = "build"
    from(if (reobfShadowJar != null) reobfShadowJar.map { it.output } else tasks.shadowJar.map { it.archiveFile })
    into(rootProject.layout.buildDirectory.file("libs/$mod_version"))
    dependsOn("build")
}

fun DependencyHandlerScope.addAdventureSerializerDependency() {
    val version = when (minecraftVersion) {
        "1.20.2" -> "4.14.0"
        "1.20.4" -> "4.16.0"
        "1.20.6", "1.21" -> "4.17.0"
        "1.21.3" -> "4.20.0"
        "1.21.5" -> "4.24.0"
        else -> if (stonecutter.eval(stonecutter.current.version, ">=1.21.6")) "4.25.0" else "4.16.0"
    }
    val dependencyNotation = "net.kyori:adventure-text-serializer-gson:$version"
    implementation(dependencyNotation)
    add(shadedDependencies.name, dependencyNotation)
}
