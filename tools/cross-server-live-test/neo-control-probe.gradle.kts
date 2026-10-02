import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService

// Explicit native client control probe only; never participates in normal builds or release packaging.
gradle.beforeProject {
    if (path == ":mods:26.2-neoforge") {
        afterEvaluate {
            val main = extensions.getByType<SourceSetContainer>().named("main")
            val toolchains = extensions.getByType<JavaToolchainService>()
            val stageDirectory = rootProject.layout.buildDirectory.dir("neo-control-native-mods")
            val compileProbe = tasks.register<JavaCompile>("compileNeoClientControl") {
                dependsOn(tasks.named("classes"))
                source(rootProject.file("tools/cross-server-live-test/NeoClientControl.java"))
                classpath = main.get().compileClasspath + main.get().output
                destinationDirectory.set(layout.buildDirectory.dir("neo-control-probe/classes"))
                javaCompiler.set(toolchains.compilerFor {
                    languageVersion.set(JavaLanguageVersion.of(25))
                })
            }
            tasks.register<Jar>("stageNeoClientControl") {
                dependsOn(compileProbe, tasks.named("shadowJar"))
                from(compileProbe.flatMap { it.destinationDirectory })
                from(rootProject.file("tools/cross-server-live-test/control.pack.mcmeta")) { rename { "pack.mcmeta" } }
                from(rootProject.file("tools/cross-server-live-test/control.neoforge.mods.toml")) { into("META-INF"); rename { "neoforge.mods.toml" } }
                archiveFileName.set("neo-control-probe.jar")
                destinationDirectory.set(stageDirectory)
            }
        }
    }
}
