import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService

// Explicit native GUI probe only; never participates in normal builds or release packaging.
gradle.beforeProject {
    if (path == ":mods:26.2-fabric") {
        afterEvaluate {
            val target = this
            val main = extensions.getByType<SourceSetContainer>().named("main")
            val toolchains = extensions.getByType<JavaToolchainService>()
            val stageDirectory = rootProject.layout.buildDirectory.dir("live-remote-gui-native-mods")
            val compileProbe = tasks.register<JavaCompile>("compileLiveRemoteGuiProbe") {
                dependsOn(tasks.named("classes"))
                source(rootProject.file("tools/cross-server-gui-test/LiveRemoteGuiProbe.java"))
                classpath = main.get().compileClasspath + main.get().output
                destinationDirectory.set(layout.buildDirectory.dir("live-remote-gui-probe/classes"))
                javaCompiler.set(toolchains.compilerFor {
                    languageVersion.set(JavaLanguageVersion.of(25))
                })
            }
            tasks.register<Jar>("stageLiveRemoteGuiProbe") {
                dependsOn(compileProbe, tasks.named("shadowJar"))
                from(compileProbe.flatMap { it.destinationDirectory })
                from(rootProject.file("tools/cross-server-gui-test/live.fabric.mod.json")) { rename { "fabric.mod.json" } }
                archiveFileName.set("live-remote-gui-probe.jar")
                destinationDirectory.set(stageDirectory)
                doLast {
                    target.copy {
                        from(tasks.named<Jar>("shadowJar").flatMap { it.archiveFile })
                        from(configurations.getByName("runtimeClasspath").files.filter {
                            it.name.matches(Regex("fabric-api-[0-9].*\\.jar"))
                                    || it.name.startsWith("fabric-permissions-api-")
                        })
                        into(stageDirectory)
                    }
                }
            }
        }
    }
}
