import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService

// Run explicitly with --init-script; this does not change production source sets or dependencies.
gradle.beforeProject {
    if (path == ":mods:26.3-fabric") {
        afterEvaluate {
            evaluationDependsOn(":common")
            val common = project(":common")
            val main = extensions.getByType(SourceSetContainer::class.java).named("main")
            val commonMain = common.extensions.getByType(SourceSetContainer::class.java).named("main")
            val toolchains = extensions.getByType(JavaToolchainService::class.java)
            val baselineSource = findProperty("issue35BaselineSource")?.toString()
            val variant = if (baselineSource == null) "candidate" else "baseline"
            val output = layout.buildDirectory.dir("issue35-probe/$variant-classes")
            val compile = tasks.register("compileIssue35Probe", JavaCompile::class.java) {
                source(rootProject.file("tools/server-performance/command-tree/CommandTreeProbe.java"))
                if (baselineSource != null) source(project.file(baselineSource))
                classpath = main.get().compileClasspath + commonMain.get().output
                destinationDirectory.set(output)
                javaCompiler.set(toolchains.compilerFor {
                    languageVersion.set(JavaLanguageVersion.of(25))
                })
                dependsOn(common.tasks.named("classes"))
            }
            tasks.register("issue35Probe", JavaExec::class.java) {
                dependsOn(compile)
                classpath = project.files(output) + main.get().runtimeClasspath
                mainClass.set("_959.server_waypoint.command.CommandTreeProbe")
                javaLauncher.set(toolchains.launcherFor {
                    languageVersion.set(JavaLanguageVersion.of(25))
                })
            }
        }
    }
}
