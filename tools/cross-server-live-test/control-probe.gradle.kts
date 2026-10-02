import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService

// Explicit native client control probe only; never participates in normal builds or release packaging.
gradle.beforeProject {
    if (path == ":mods:26.2-forge") {
        afterEvaluate {
            val main = extensions.getByType<SourceSetContainer>().named("main")
            val toolchains = extensions.getByType<JavaToolchainService>()
            val stageDirectory = rootProject.layout.buildDirectory.dir("native-control-native-mods")
            val compileProbe = tasks.register<JavaCompile>("compileNativeClientControl") {
                dependsOn(tasks.named("classes"))
                source(rootProject.file("tools/cross-server-live-test/NativeClientControl.java"))
                classpath = main.get().compileClasspath + main.get().output
                destinationDirectory.set(layout.buildDirectory.dir("native-control-probe/classes"))
                javaCompiler.set(toolchains.compilerFor {
                    languageVersion.set(JavaLanguageVersion.of(25))
                })
            }
            tasks.register<Jar>("stageNativeClientControl") {
                dependsOn(compileProbe, tasks.named("shadowJar"))
                from(compileProbe.flatMap { it.destinationDirectory })
                from(rootProject.file("tools/cross-server-live-test/control.pack.mcmeta")) { rename { "pack.mcmeta" } }
                from(rootProject.file("tools/cross-server-live-test/control.mods.toml")) { into("META-INF"); rename { "mods.toml" } }
                archiveFileName.set("native-control-probe.jar")
                destinationDirectory.set(stageDirectory)
            }
        }
    }
}
