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
val targetJavaVersion = when {
    stonecutter.eval(stonecutter.current.version, ">=26") -> 25
    stonecutter.eval(stonecutter.current.version, ">=1.20.5") -> 21
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
val usesSrgMappings = !stonecutter.eval(mcVersion, ">=26")
evaluationDependsOn(":common")
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

val mixinMappingsArchive = providers.provider {
    val mappingsDir = rootProject.layout.projectDirectory
        .dir(".gradle/mavenizer/repo/net/minecraft/mappings_official")
        .asFile
    mappingsDir
        .listFiles { file -> file.isDirectory && file.name.startsWith("$mcVersion-") }
        ?.asSequence()
        ?.map { file -> file.resolve("mappings_official-${file.name}-map2srg.tsrg.gz") }
        ?.firstOrNull { file -> file.isFile }
        ?: throw GradleException("Missing Forge official-to-SRG mappings for Minecraft $mcVersion")
}
val unpackedMixinMappings = layout.buildDirectory.file("mixin/official-to-srg.tsrg")
val devRefmapRemappingFile = layout.buildDirectory.file("mixin/srg-to-official.srg")
val mixinRefmapFile = layout.buildDirectory.file("classes/java/main/$mixinRefmap")
val mixinSrgFile = layout.buildDirectory.file("tmp/compileJava/server_waypoint-common-mixins.srg")

val unpackMixinMappings by tasks.registering {
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

val createMixinDevRemapMappings by tasks.registering {
    inputs.file(unpackedMixinMappings)
    outputs.file(devRefmapRemappingFile)
    dependsOn(unpackMixinMappings)

    doLast {
        val output = devRefmapRemappingFile.get().asFile
        output.parentFile.mkdirs()

        var officialClass: String? = null
        var srgClass: String? = null
        output.printWriter().use { writer ->
            unpackedMixinMappings.get().asFile.forEachLine { line ->
                if (line.isBlank() || line.startsWith("tsrg")) {
                    return@forEachLine
                }

                val parts = line.trim().split(Regex("\\s+"))
                if (!line.startsWith("\t")) {
                    officialClass = parts.getOrNull(0)
                    srgClass = parts.getOrNull(1)
                    return@forEachLine
                }

                val currentOfficialClass = officialClass ?: return@forEachLine
                val currentSrgClass = srgClass ?: return@forEachLine
                when {
                    parts.size == 2 && parts[0].firstOrNull()?.isDigit() != true -> {
                        writer.println("FD: $currentSrgClass/${parts[1]} $currentOfficialClass/${parts[0]}")
                    }
                    parts.size == 3 && parts[1].startsWith("(") -> {
                        writer.println("MD: $currentSrgClass/${parts[2]} ${parts[1]} $currentOfficialClass/${parts[0]} ${parts[1]}")
                    }
                }
            }
        }
    }
}

configure<net.minecraftforge.gradle.MinecraftExtensionForProject> {
    mappings("official", mcVersion)

    runs {
        create("client") {
            workingDir.set(project.layout.projectDirectory.dir("run"))
            systemProperty("forge.logging.markers", "REGISTRIES")
            systemProperty("forge.logging.console.level", "debug")
            if (usesSrgMappings) {
                systemProperty("mixin.env.remapRefMap", "true")
                systemProperty("mixin.env.refMapRemappingFile", devRefmapRemappingFile.get().asFile.absolutePath)
            }
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
            if (usesSrgMappings) {
                systemProperty("mixin.env.remapRefMap", "true")
                systemProperty("mixin.env.refMapRemappingFile", devRefmapRemappingFile.get().asFile.absolutePath)
            }
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
    implementation(minecraftExtension.dependency("net.minecraftforge:forge:$mcVersion-$forge_loader").asProvider())
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
    val forgeDependency = "[${forge_loader.substringBefore(".")},)"
    inputs.property("minecraft_dependency", mcVersionForge)
    inputs.property("forge_dependency", forgeDependency)
    filesMatching("META-INF/mods.toml") {
        expand(mapOf(
            "id" to mod_id,
            "name" to mod_name,
            "version" to mod_version,
            "minecraft_dependency" to mcVersionForge,
            "forge_dependency" to forgeDependency,
        ))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
}

tasks.named<JavaCompile>("compileJava") {
    outputs.file(mixinRefmapFile)
    options.compilerArgs.add("-AoutRefMapFile=${mixinRefmapFile.get().asFile.absolutePath}")
    if (usesSrgMappings) {
        dependsOn(unpackMixinMappings)
        inputs.file(unpackedMixinMappings)
        options.compilerArgs.addAll(listOf(
            "-AreobfTsrgFile=${unpackedMixinMappings.get().asFile.absolutePath}",
            "-AoutTsrgFile=${mixinSrgFile.get().asFile.absolutePath}",
            "-AmappingTypes=tsrg",
            "-AdefaultObfuscationEnv=searge"
        ))
    }
}

tasks.named("compileJava") {
    dependsOn("stonecutterGenerate")
}

tasks.named("processResources") {
    dependsOn("stonecutterGenerate")
}

tasks.configureEach {
    if (usesSrgMappings && (name == "runClient" || name == "runServer" || name == "runTestClient" || name == "runTestServer")) {
        dependsOn(createMixinDevRemapMappings)
    }
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

val shadowJarTask = tasks.named<ShadowJar>("shadowJar")
val reobfShadowJar = if (usesSrgMappings) {
    val renamedShadowJar = extensions
        .getByType(net.minecraftforge.renamer.gradle.RenamerExtension::class.java)
        .classes("renameShadowJar", shadowJarTask) {
            archiveClassifier.set("renamed")
            output.set(layout.buildDirectory.file("tmp/renameShadowJar/${base.archivesName.get()}.jar"))
            dependsOn(unpackMixinMappings)
            setMappings(files(unpackedMixinMappings))
        }

    tasks.register<Zip>("reobfShadowJar") {
        group = "build"
        archiveFileName.set(base.archivesName.map { "$it.jar" })
        destinationDirectory.set(layout.buildDirectory.dir("libs"))
        from(renamedShadowJar.flatMap { it.output }.map { zipTree(it.asFile) })
        exclude("fernflower_abstract_parameter_names.txt")
        dependsOn(renamedShadowJar)
    }
} else {
    tasks.register<Zip>("reobfShadowJar") {
        group = "build"
        archiveFileName.set(base.archivesName.map { "$it.jar" })
        destinationDirectory.set(layout.buildDirectory.dir("libs"))
        from(shadowJarTask.flatMap { it.archiveFile }.map { zipTree(it.asFile) })
        exclude("fernflower_abstract_parameter_names.txt")
        dependsOn(shadowJarTask)
    }
}

shadowJarTask {
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
    val version = when (mcVersion) {
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
