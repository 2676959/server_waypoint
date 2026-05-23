import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import net.minecraftforge.gradle.shadow.net.minecraftforge.gradleutils.shared.ToolsExtension
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.GZIPInputStream

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
val mixinConfig = "server_waypoint-common.mixins.json"
val mixinRefmap = "server_waypoint-common.refmap.json"
val commonMainSourceSet = project(":common")
    .extensions
    .getByType(org.gradle.api.tasks.SourceSetContainer::class.java)
    .named("main")
val forgeRunRuntimeDir = layout.buildDirectory.dir("forgeRunRuntime/classes")

val copyForgeRunRuntime by tasks.registering(Sync::class) {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(commonMainSourceSet.map { it.output })
    from({
        shadedDependencies
            .filter {
                it.isFile && it.extension == "jar" && (
                    it.name.startsWith("adventure-") ||
                        it.name.startsWith("examination-") ||
                        it.name.startsWith("option-")
                )
            }
            .map { zipTree(it) }
    })
    exclude("META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.SF", "META-INF/MANIFEST.MF", "mappings/**")
    into(forgeRunRuntimeDir)
    dependsOn(project(":common").tasks.named("classes"), project(":common").tasks.named("jar"))
}

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
    }
    resources {
        exclude("fabric.mod.json")
        exclude("META-INF/neoforge.mods.toml")
        exclude("server_waypoint-fabric.mixins.json")
    }
}

val forgeRunRuntimeSourceSet = sourceSets.create("forgeRunRuntime") {
    java.setSrcDirs(emptyList<String>())
    resources.setSrcDirs(emptyList<String>())
    output.dir(mapOf("builtBy" to copyForgeRunRuntime), forgeRunRuntimeDir)
}

repositories {
    mavenCentral()
    maven("https://libraries.minecraft.net")
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

configure<net.minecraftforge.gradle.MinecraftExtensionForProject> {
    mappings("official", mcVersion)

    runs {
        create("client") {
            workingDir.set(project.layout.projectDirectory.dir("run"))
            systemProperty("forge.logging.markers", "REGISTRIES")
            systemProperty("forge.logging.console.level", "debug")
            args("-mixin.config=$mixinConfig")
            mods {
                create(mod_id) {
                    source(sourceSets.main.get())
                    source(forgeRunRuntimeSourceSet)
                }
            }
        }
        create("server") {
            workingDir.set(project.layout.projectDirectory.dir("run"))
            systemProperty("forge.logging.markers", "REGISTRIES")
            systemProperty("forge.logging.console.level", "debug")
            args("-mixin.config=$mixinConfig")
            mods {
                create(mod_id) {
                    source(sourceSets.main.get())
                    source(forgeRunRuntimeSourceSet)
                }
            }
        }
    }
}

val minecraftExtension = extensions.getByType<net.minecraftforge.gradle.MinecraftExtensionForProject>()
minecraftExtension.mavenizer(repositories)

dependencies {
    implementation(minecraftExtension.dependency("net.minecraftforge:forge:$mcVersion-${property("forge_loader")}").asProvider())
    annotationProcessor("org.spongepowered:mixin:0.8.5:processor")

    implementation(project(":common"))
    add(shadedDependencies.name, project(":common"))
    addAdventureSerializerDependency()

    val xaeros_minimap_forge: String by project
    compileOnly("maven.modrinth:xaeros-minimap:$xaeros_minimap_forge")
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
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
}

val mixinMappingsVersion = "$mcVersion-20230612.114412"
val mixinMappingsArchive = rootProject.layout.projectDirectory.file(
    ".gradle/mavenizer/repo/net/minecraft/mappings_official/$mixinMappingsVersion/mappings_official-$mixinMappingsVersion-map2srg.tsrg.gz"
)
val unpackedMixinMappings = layout.buildDirectory.file("mixin/official-to-srg.tsrg")
val mixinRefmapFile = layout.buildDirectory.file("classes/java/main/$mixinRefmap")
val mixinSrgFile = layout.buildDirectory.file("tmp/compileJava/server_waypoint-common-mixins.srg")

val unpackMixinMappings by tasks.registering {
    inputs.file(mixinMappingsArchive)
    outputs.file(unpackedMixinMappings)

    doLast {
        val output = unpackedMixinMappings.get().asFile
        output.parentFile.mkdirs()
        GZIPInputStream(mixinMappingsArchive.asFile.inputStream()).use { input ->
            Files.copy(input, output.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

tasks.named<JavaCompile>("compileJava") {
    dependsOn(unpackMixinMappings)
    inputs.file(unpackedMixinMappings)
    outputs.file(mixinRefmapFile)
    options.compilerArgs.addAll(listOf(
        "-AoutRefMapFile=${mixinRefmapFile.get().asFile.absolutePath}",
        "-AreobfTsrgFile=${unpackedMixinMappings.get().asFile.absolutePath}",
        "-AoutTsrgFile=${mixinSrgFile.get().asFile.absolutePath}",
        "-AmappingTypes=tsrg",
        "-AdefaultObfuscationEnv=searge"
    ))
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
            "MixinConfigs" to mixinConfig,
        ))
    }
    from(rootProject.file("LICENSE")) {
        rename { "${it}_$mod_name" }
    }
}

val renamedShadowJar = extensions
    .getByType(net.minecraftforge.renamer.gradle.RenamerExtension::class.java)
    .classes("renameShadowJar", tasks.named<ShadowJar>("shadowJar")) {
        archiveClassifier.set("renamed")
        output.set(layout.buildDirectory.file("tmp/renameShadowJar/${base.archivesName.get()}.jar"))
        dependsOn(unpackMixinMappings)
        setMappings(files(unpackedMixinMappings))
    }

val reobfShadowJar by tasks.registering(Zip::class) {
    group = "build"
    archiveFileName.set(base.archivesName.map { "$it.jar" })
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    from(renamedShadowJar.flatMap { it.output }.map { zipTree(it.asFile) })
    exclude("fernflower_abstract_parameter_names.txt")
    dependsOn(renamedShadowJar)
}

tasks.named<ShadowJar>("shadowJar") {
    configurations = listOf(shadedDependencies)
    addMultiReleaseAttribute.set(false)
    archiveClassifier.set("dev-shadow")
    dependencies {
        include(project(":common"))
        include(dependency("net.kyori:.*"))
    }
}

tasks.assemble {
    dependsOn(reobfShadowJar)
}

artifacts {
    archives(reobfShadowJar)
}

tasks.register<Copy>("buildAndCollect") {
    group = "build"
    from(reobfShadowJar.map { it.archiveFile })
    into(rootProject.layout.buildDirectory.file("libs/$mod_version"))
    dependsOn("build")
}

fun DependencyHandlerScope.addAdventureSerializerDependency() {
    val dependencyNotation = "net.kyori:adventure-text-serializer-gson:4.14.0"
    implementation(dependencyNotation)
    add(shadedDependencies.name, dependencyNotation)
}
