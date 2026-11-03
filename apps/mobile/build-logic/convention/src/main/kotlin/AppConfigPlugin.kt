import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import template.tooling.EnvConfig
import template.tooling.Repo

/**
 * `papy.appconfig` — 선택된 환경(env/<env>.properties)으로 commonMain 에 `AppConfig` 상수를 생성한다 (core:data 에 적용).
 * core 는 AppConfig 만 보고, 환경을 고르는 일은 빌드가 한다(DEC-058).
 */
class AppConfigPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        val env = appEnvironment()
        val repoDir = rootProject.layout.projectDirectory
        val repo = Repo(repoDir.asFile)
        val task = tasks.register<GenerateAppConfigTask>("generateAppConfig") {
            description = "env/*.properties($env) → AppConfig.kt"
            environment.set(env)
            repoRoot.set(repoDir)
            envFiles.from(EnvConfig.ENVIRONMENTS.flatMap { EnvConfig(repo).inputFiles(it) })
            outputDir.set(layout.buildDirectory.dir("generated/appconfig/commonMain/kotlin"))
        }
        extensions.configure<KotlinMultiplatformExtension> {
            sourceSets.getByName("commonMain").kotlin.srcDir(task.flatMap { it.outputDir })
        }
    }
}

abstract class GenerateAppConfigTask : DefaultTask() {
    @get:Input
    abstract val environment: Property<String>

    @get:Internal
    abstract val repoRoot: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val envFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val repo = Repo(repoRoot.get().asFile)
        val config = EnvConfig(repo)
        config.consistencyErrors().takeIf { it.isNotEmpty() }?.let { throw org.gradle.api.GradleException(it.joinToString("\n")) }
        val out = outputDir.get().asFile
        out.deleteRecursively()
        val file = out.resolve(repo.basePath + "/core/data/config/AppConfig.kt")
        file.parentFile.mkdirs()
        file.writeText(config.kotlinAppConfig(environment.get()))
        logger.info("AppConfig: ${environment.get()}")
    }
}
