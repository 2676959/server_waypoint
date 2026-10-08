import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.plugins.BasePluginExtension

// Explicit test preparation only. No source set or probe is added to production artifacts.
gradle.beforeProject {
    if (path.startsWith(":mods:") && findProperty("liveGameOutput") != null) {
        afterEvaluate {
            val target = this
            val main = extensions.getByType<SourceSetContainer>().named("main")
            val loader = name.substringAfterLast('-')
            val minecraft = name.substringBeforeLast('-')
            val tools = configurations.create("liveGameTools") {
                isCanBeConsumed = false
                isCanBeResolved = true
            }
            repositories.maven { url = uri("https://maven.fabricmc.net/") }
            dependencies.add(tools.name, "org.ow2.asm:asm:9.8")
            dependencies.add(tools.name, "org.ow2.asm:asm-tree:9.8")
            dependencies.add(tools.name, "com.google.code.gson:gson:2.11.0")
            dependencies.add(tools.name, "net.fabricmc:tiny-remapper:0.10.3:fat")
            val mods = configurations.create("liveGameMods") {
                isCanBeConsumed = false
                isCanBeResolved = true
                isTransitive = false
            }
            for (mod in listOf("xaeros_minimap", "xaeros_world_map", "voxelmap")) {
                val pin = findProperty("${mod}_$loader")?.toString() ?: continue
                val artifact = when (mod) {
                    "xaeros_minimap" -> "xaeros-minimap"
                    "xaeros_world_map" -> "xaeros-world-map"
                    else -> "voxelmap-updated"
                }
                dependencies.add(mods.name, "maven.modrinth:$artifact:$pin")
            }
            findProperty("xaerolib_$loader")?.let { pin ->
                val libMinecraft = findProperty("xaerolib_${loader}_minecraft")?.toString() ?: minecraft
                dependencies.add(mods.name, "xaero.lib:xaerolib-$loader-$libMinecraft:$pin")
            }
            if (loader == "fabric") {
                dependencies.add(mods.name, "net.fabricmc.fabric-api:fabric-api:${property("fabric_api")}")
            }
            tasks.register("liveGameTestInputs") {
                dependsOn(tasks.named("assemble"))
                doLast {
                    val output = target.rootProject.file(target.property("liveGameOutput").toString()).resolve("inputs/${target.name}")
                    output.mkdirs()
                    fun paths(name: String, values: Iterable<java.io.File>) {
                        output.resolve(name).writeText(values.joinToString("\n") { it.absolutePath } + "\n")
                    }
                    paths("compile.paths", (main.get().output + main.get().compileClasspath).files)
                    paths("mods.paths", mods.files)
                    paths("tools.paths", tools.files.sortedBy { if (it.name.startsWith("tiny-remapper")) 1 else 0 })
                    val archiveName = target.extensions.getByType<BasePluginExtension>().archivesName.get()
                    paths("production.paths", listOf(target.layout.buildDirectory.file("libs/$archiveName.jar").get().asFile))
                    if (loader == "fabric" && minecraft.substringBefore('.').toInt() < 26) {
                        val loom = target.extensions.getByName("loom")
                        val mappings = loom.javaClass.getMethod("getMappingsFile").invoke(loom) as java.io.File
                        paths("mappings.paths", listOf(mappings))
                    } else if (loader == "forge" && minecraft.split('.').map(String::toInt).let {
                            it[0] == 1 && it[1] == 20 && it.getOrElse(2) { 0 } < 6
                        }) {
                        paths("mappings.paths", listOf(target.layout.buildDirectory.file("mixin/official-to-srg.tsrg").get().asFile))
                    }
                }
            }
        }
    }
}
