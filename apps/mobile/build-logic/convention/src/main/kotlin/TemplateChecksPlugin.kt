import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register
import template.tooling.ArchRules
import template.tooling.DesignTokens
import template.tooling.EnvConfig
import template.tooling.Repo
import template.tooling.StringsCheck

/**
 * `elsa.template` — 루트 프로젝트에 템플릿 점검·생성 태스크를 등록한다 (Python 스크립트 대체, DEC-056).
 *
 *   ./gradlew templateCheck            디자인 토큰 최신 + 문자열 키 일치 + 아키텍처 규칙
 *   ./gradlew generateDesignTokens     design/tokens.json → Kotlin/Swift/XML 생성물
 *   ./gradlew generateEnvConfig        env/<env>.properties → iOS Env.generated.xcconfig
 *
 * 이 태스크들은 소스를 읽기만 하거나(점검) 토큰 생성물만 쓴다. settings·패키지를 바꾸는 newFeature·renameApp 은
 * 루트 빌드 밖의 build-logic tooling 으로 실행한다(DEC-057).
 */
class TemplateChecksPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        require(this == rootProject) { "elsa.template 은 루트 프로젝트에만 적용한다" }

        val tokens = tasks.register<CheckDesignTokensTask>("checkDesignTokens") {
            group = GROUP
            description = "design/tokens.json 생성물이 최신인지 확인"
            repoRoot.set(layout.projectDirectory)
        }
        val strings = tasks.register<CheckStringsTask>("checkStrings") {
            group = GROUP
            description = "Android strings.xml(ko/en) ↔ iOS Localizable.xcstrings 키·인자 일치"
            repoRoot.set(layout.projectDirectory)
        }
        val arch = tasks.register<CheckArchitectureTask>("checkArchitecture") {
            group = GROUP
            description = "모듈 의존 그래프·소스 규칙 (docs/conventions.md)"
            repoRoot.set(layout.projectDirectory)
            // 실제 Gradle 의존 그래프: 프로젝트 → 모든 configuration 의 프로젝트 의존
            projectDependencies.set(provider {
                subprojects.associate { p ->
                    p.path to p.configurations.flatMap { c ->
                        c.dependencies.withType(ProjectDependency::class.java).map { it.path }
                    }.filter { it != p.path }.distinct().sorted()
                }
            })
        }
        val env = tasks.register<CheckEnvConfigTask>("checkEnvConfig") {
            group = GROUP
            description = "env/*.properties 키 일치 + iOS Env.generated.xcconfig 최신"
            repoRoot.set(layout.projectDirectory)
        }
        tasks.register("templateCheck") {
            group = GROUP
            description = "템플릿 점검 전체 (토큰·문자열·아키텍처·환경)"
            dependsOn(tokens, strings, arch, env)
        }
        tasks.register<GenerateEnvConfigTask>("generateEnvConfig") {
            group = GROUP
            description = "env/*.properties → iosApp/Configuration/Env.generated.xcconfig"
            repoRoot.set(layout.projectDirectory)
        }
        tasks.register<GenerateDesignTokensTask>("generateDesignTokens") {
            group = GROUP
            description = "design/tokens.json → Android/iOS 토큰 생성물"
            repoRoot.set(layout.projectDirectory)
        }
        Unit
    }

    private companion object {
        const val GROUP = "template"
    }
}

abstract class TemplateTask : DefaultTask() {
    @get:Internal
    abstract val repoRoot: DirectoryProperty

    protected fun repo() = Repo(repoRoot.get().asFile)
}

abstract class CheckDesignTokensTask : TemplateTask() {
    @TaskAction
    fun check() {
        val stale = DesignTokens(repo()).stale()
        if (stale.isNotEmpty()) {
            throw GradleException("토큰 생성물이 오래되었습니다. ./gradlew generateDesignTokens 후 커밋:\n  " + stale.joinToString("\n  "))
        }
        logger.lifecycle("디자인 토큰 최신")
    }
}

abstract class GenerateDesignTokensTask : TemplateTask() {
    @TaskAction
    fun generate() {
        val written = DesignTokens(repo()).generate()
        logger.lifecycle(if (written.isEmpty()) "디자인 토큰 최신 (변경 없음)" else written.joinToString("\n") { "생성: $it" })
    }
}

abstract class CheckEnvConfigTask : TemplateTask() {
    @TaskAction
    fun check() {
        val repo = repo()
        val config = EnvConfig(repo)
        val errors = config.consistencyErrors().toMutableList()
        val xcconfig = repo.file(EnvConfig.IOS_XCCONFIG)
        if (!xcconfig.isFile || xcconfig.readText() != config.iosXcconfig()) {
            errors += "${EnvConfig.IOS_XCCONFIG} 가 env/*.properties 와 다르다 → ./gradlew generateEnvConfig 후 커밋"
        }
        if (errors.isNotEmpty()) throw GradleException("환경 설정 점검 실패:\n  " + errors.joinToString("\n  "))
        logger.lifecycle("환경 설정 일치 (${EnvConfig.ENVIRONMENTS.joinToString()})")
    }
}

abstract class GenerateEnvConfigTask : TemplateTask() {
    @TaskAction
    fun generate() {
        val repo = repo()
        val text = EnvConfig(repo).iosXcconfig()
        val file = repo.file(EnvConfig.IOS_XCCONFIG)
        if (file.isFile && file.readText() == text) {
            logger.lifecycle("${EnvConfig.IOS_XCCONFIG} 최신 (변경 없음)")
        } else {
            file.writeText(text)
            logger.lifecycle("생성: ${EnvConfig.IOS_XCCONFIG}")
        }
        // 소셜 URL 스킴(비밀 키에서 나옴 → 커밋 안 함, DEC-119)
        val social = repo.file(EnvConfig.IOS_SOCIAL_XCCONFIG)
        val socialText = EnvConfig(repo).iosSocialXcconfig()
        if (!social.isFile || social.readText() != socialText) {
            social.writeText(socialText)
            logger.lifecycle("생성: ${EnvConfig.IOS_SOCIAL_XCCONFIG} (커밋 안 함)")
        }
    }
}

abstract class CheckStringsTask : TemplateTask() {
    @TaskAction
    fun check() {
        val result = StringsCheck(repo()).run()
        if (result.errors.isNotEmpty()) throw GradleException("문자열 키 점검 실패:\n  " + result.errors.joinToString("\n  "))
        logger.lifecycle(result.summary)
    }
}

abstract class CheckArchitectureTask : TemplateTask() {
    @get:Input
    abstract val projectDependencies: MapProperty<String, List<String>>

    @TaskAction
    fun check() {
        val graph = projectDependencies.get().mapValues { it.value.toSet() }
        val result = ArchRules(repo(), graph).run()
        if (result.errors.isNotEmpty()) throw GradleException("아키텍처 규칙 위반:\n  " + result.errors.joinToString("\n  "))
        logger.lifecycle(result.summary)
    }
}
