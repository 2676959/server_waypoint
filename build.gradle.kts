import com.modrinth.minotaur.ModrinthExtension
import com.modrinth.minotaur.dependencies.ModDependency
import net.darkhax.curseforgegradle.TaskPublishCurseForge
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.jvm.tasks.Jar

plugins {
    id("net.fabricmc.fabric-loom") version "1.15.5" apply false
    id("net.fabricmc.fabric-loom-remap") version "1.15.5" apply false
    id("net.minecraftforge.gradle") version "[7.0.11,8.0)" apply false
    id("net.minecraftforge.renamer") version "1.1.0" apply false
    id("net.neoforged.moddev") version "2.0.141" apply false
    id("net.neoforged.gradle.userdev") version "7.1.27" apply false
    id("com.gradleup.shadow") version "9.4.1" apply false
    id("com.modrinth.minotaur") version "2.9.0" apply false
    id("net.darkhax.curseforgegradle") version "1.1.28" apply false
}

// Xaero's Minimap has no 1.21.2 build, so these 1.21.3 targets are retained only as
// development environments for testing the identical 1.21.2-1.21.4 integration code.
// The overlapping 1.21.2 targets provide the published artifacts for that full range.
// Forge 1.21.3 remains publishable because Forge does not provide a 1.21.2 loader.
val developmentOnlyProjectPaths = setOf(
    ":mods:1.21.3-fabric",
    ":mods:1.21.3-neoforge",
)
val modrinthProjectPaths = listOf(project(":mods"), project(":paper"))
    .flatMap { it.subprojects }
    .map { it.path }
    .filterNot(developmentOnlyProjectPaths::contains)
    .sorted()
val modrinthUploadLock = gradle.sharedServices.registerIfAbsent(
    "modrinthUploadLock",
    ModrinthUploadLock::class,
) {
    maxParallelUsages.set(1)
}
val curseForgeProjectPaths = project(":mods")
    .subprojects
    .map { it.path }
    .filterNot(developmentOnlyProjectPaths::contains)
    .sorted()
val curseForgeUploadLock = gradle.sharedServices.registerIfAbsent(
    "curseForgeUploadLock",
    CurseForgeUploadLock::class,
) {
    maxParallelUsages.set(1)
}

allprojects {
    repositories {
        mavenCentral()
    }

    pluginManager.withPlugin("com.modrinth.minotaur") {
        tasks.named("modrinth") {
            group = "publishing"
            description = "Builds and uploads the ${project.name} artifact to Modrinth."
            enabled = project.path !in developmentOnlyProjectPaths
            dependsOn("build")
            usesService(modrinthUploadLock)
        }

        afterEvaluate {
            val targetLoader = when {
                project.name.endsWith("-fabric") -> "fabric"
                project.name.endsWith("-forge") -> "forge"
                project.name.endsWith("-neoforge") -> "neoforge"
                project.name.endsWith("-paper") -> "paper"
                else -> error("Cannot determine the Modrinth loader for ${project.path}")
            }

            val modName = property("mod_name") as String
            val modVersion = property("mod_version") as String
            val modrinthProjectId = property("modrinth_project_id") as String
            val minecraftVersionRange = property("mcVersionRange") as String
            val minecraftVersions = findProperty("modrinthGameVersions")
                ?.toString()
                ?.split(',')
                ?.map(String::trim)
                ?.filter(String::isNotEmpty)
                ?: expandMinecraftVersionRange(minecraftVersionRange)
            val archiveName = extensions.getByType<BasePluginExtension>().archivesName
            val uploadArtifact = layout.buildDirectory.file(archiveName.map { "libs/$it.jar" })

            extensions.configure<ModrinthExtension> {
                token.set(providers.environmentVariable("MODRINTH_TOKEN"))
                projectId.set(modrinthProjectId)
                versionNumber.set(modVersion)
                versionName.set(buildString {
                    append(modName)
                    append(' ')
                    append(modVersion)
                    when (targetLoader) {
                        "forge" -> append(" Forge")
                        "neoforge" -> append(" NeoForge")
                        "paper" -> append(" Paper")
                    }
                })
                versionType.set(providers.gradleProperty("modrinthVersionType").orElse("release"))
                changelog.set(
                    providers.gradleProperty("modrinthChangelog")
                        .orElse(providers.environmentVariable("MODRINTH_CHANGELOG"))
                        .orElse(ModrinthExtension.DEFAULT_CHANGELOG)
                )
                file.set(uploadArtifact)
                gameVersions.set(minecraftVersions)
                loaders.set(when (targetLoader) {
                    "fabric" -> listOf("fabric", "quilt")
                    "paper" -> listOf("paper", "purpur")
                    else -> listOf(targetLoader)
                })
                dependencies.set(when (targetLoader) {
                    "fabric" -> listOf(
                        ModDependency("P7dR8mSH", "required"),
                        ModDependency("Vebnzrzj", "optional"),
                        ModDependency("1bokaNcj", "optional"),
                    )
                    "forge", "neoforge" -> listOf(ModDependency("1bokaNcj", "optional"))
                    else -> emptyList()
                })
                detectLoaders.set(false)
                autoAddDependsOn.set(false)
                debugMode.set(providers.gradleProperty("modrinthDebug").map { it.toBoolean() }.orElse(false))
            }
        }
    }

    pluginManager.withPlugin("net.darkhax.curseforgegradle") {
        tasks.register<TaskPublishCurseForge>("curseforge") {
            group = "publishing"
            description = "Builds and uploads the ${project.name} artifact to CurseForge."
            enabled = project.path !in developmentOnlyProjectPaths
            dependsOn("build")
            usesService(curseForgeUploadLock)

            val targetLoader = when {
                project.name.endsWith("-fabric") -> "fabric"
                project.name.endsWith("-forge") -> "forge"
                project.name.endsWith("-neoforge") -> "neoforge"
                else -> error("Cannot determine the CurseForge loader for ${project.path}")
            }
            val curseForgeProjectId = project.property("curseforge_project_id") as String
            val minecraftVersionRange = project.property("mcVersionRange") as String
            val minecraftVersions = expandMinecraftVersionRange(minecraftVersionRange)
            val archiveName = project.extensions.getByType<BasePluginExtension>().archivesName
            val uploadArtifact = project.layout.buildDirectory.file(archiveName.map { "libs/$it.jar" })
            val isDebug = project.providers.gradleProperty("curseforgeDebug")
                .map(String::toBoolean)
                .getOrElse(false)
            val curseForgeToken = project.providers.environmentVariable("CURSEFORGE_TOKEN")
                .orElse(project.providers.gradleProperty("curseforgeToken"))

            apiToken = curseForgeToken.orElse("").get()
            debugMode = isDebug
            disableVersionDetection()
            doFirst {
                if (curseForgeToken.orNull.isNullOrBlank()) {
                    throw GradleException(
                        "Set CURSEFORGE_TOKEN or -PcurseforgeToken before running ${project.path}:curseforge."
                    )
                }
            }

            val mainFile = upload(curseForgeProjectId, uploadArtifact)
            mainFile.changelog = project.providers.gradleProperty("curseforgeChangelog")
                .orElse(project.providers.environmentVariable("MODRINTH_CHANGELOG"))
                .orElse("No changelog was provided.")
                .get()
            mainFile.changelogType = "markdown"
            mainFile.releaseType = project.providers.gradleProperty("curseforgeReleaseType")
                .orElse("release")
                .get()
            mainFile.addGameVersion(*minecraftVersions.toTypedArray())
            mainFile.addEnvironment("Client", "Server")
            when (targetLoader) {
                "fabric" -> {
                    mainFile.addModLoader("Fabric", "Quilt")
                    mainFile.addRequirement("fabric-api")
                    mainFile.addOptional("luckperms", "xaeros-minimap")
                }
                "forge" -> {
                    mainFile.addModLoader("Forge")
                    mainFile.addOptional("xaeros-minimap")
                }
                "neoforge" -> {
                    mainFile.addModLoader("NeoForge")
                    mainFile.addOptional("xaeros-minimap")
                }
            }
        }
    }
}

subprojects {
    tasks.withType<Jar>().configureEach {
        from(rootProject.file("CREDITS.txt")) {
            rename { "SERVER_WAYPOINT_CREDITS.txt" }
        }
    }
}

fun registerModrinthBranchTask(taskName: String, loader: String, displayName: String) = tasks.register(taskName) {
    group = "publishing"
    description = "Builds and uploads every supported $displayName artifact to Modrinth."
    dependsOn(
        modrinthProjectPaths
            .filter { it.endsWith("-$loader") }
            .map { "$it:modrinth" }
    )
}

val publishModrinthFabric = registerModrinthBranchTask("publishModrinthFabric", "fabric", "Fabric")
val publishModrinthForge = registerModrinthBranchTask("publishModrinthForge", "forge", "Forge")
val publishModrinthNeoForge = registerModrinthBranchTask("publishModrinthNeoForge", "neoforge", "NeoForge")
val publishModrinthPaper = registerModrinthBranchTask("publishModrinthPaper", "paper", "Paper")

tasks.register("publishModrinth") {
    group = "publishing"
    description = "Builds and uploads every supported artifact to Modrinth. Use -PmodrinthDebug=true for a dry run."
    dependsOn(
        publishModrinthFabric,
        publishModrinthForge,
        publishModrinthNeoForge,
        publishModrinthPaper,
    )
}

fun registerCurseForgeBranchTask(taskName: String, loader: String, displayName: String) = tasks.register(taskName) {
    group = "publishing"
    description = "Builds and uploads every supported $displayName artifact to CurseForge."
    dependsOn(
        curseForgeProjectPaths
            .filter { it.endsWith("-$loader") }
            .map { "$it:curseforge" }
    )
}

val publishCurseForgeFabric = registerCurseForgeBranchTask("publishCurseForgeFabric", "fabric", "Fabric")
val publishCurseForgeForge = registerCurseForgeBranchTask("publishCurseForgeForge", "forge", "Forge")
val publishCurseForgeNeoForge = registerCurseForgeBranchTask("publishCurseForgeNeoForge", "neoforge", "NeoForge")

tasks.register("publishCurseForge") {
    group = "publishing"
    description = "Builds and uploads every supported mod artifact to CurseForge. Debug mode still requires CURSEFORGE_TOKEN."
    dependsOn(
        publishCurseForgeFabric,
        publishCurseForgeForge,
        publishCurseForgeNeoForge,
    )
}

fun expandMinecraftVersionRange(versionRange: String): List<String> {
    val bounds = versionRange.split('-', limit = 2)
    if (bounds.size == 1) {
        return bounds
    }

    val start = bounds[0].split('.').map(String::toInt)
    val end = bounds[1].split('.').map(String::toInt)
    require(start.size in 2..3 && end.size == 3 && start.take(2) == end.take(2)) {
        "Cannot expand Minecraft version range '$versionRange'; set modrinthGameVersions explicitly."
    }

    val prefix = start.take(2).joinToString(".")
    val startPatch = start.getOrElse(2) { 0 }
    return (startPatch..end[2]).map { patch ->
        if (patch == 0 && start.size == 2) prefix else "$prefix.$patch"
    }
}

abstract class ModrinthUploadLock : BuildService<BuildServiceParameters.None>, AutoCloseable {
    override fun close() = Unit
}

abstract class CurseForgeUploadLock : BuildService<BuildServiceParameters.None>, AutoCloseable {
    override fun close() = Unit
}
