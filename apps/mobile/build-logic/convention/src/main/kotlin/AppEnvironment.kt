import org.gradle.api.GradleException
import org.gradle.api.Project
import template.tooling.EnvConfig

/**
 * 이번 빌드의 앱 환경(dev / staging / prod). 모든 모듈이 같은 값을 쓴다 (DEC-058).
 *
 * 우선순위
 *   1. -Papp.env=<env>                              (orca check --env, CI)
 *   2. 환경변수 APP_ENV                              (Xcode 빌드 단계 — xcconfig 의 APP_ENV 가 내보내짐)
 *   3. 요청한 Android 태스크의 flavor               (assembleStagingDebug, Android Studio 실행 등)
 *   4. dev
 * 요청 태스크에 flavor 가 둘 이상이면 실패한다(KMP 공통 코드는 한 빌드에 한 환경만 가질 수 있음).
 */
fun Project.appEnvironment(): String {
    val envs = EnvConfig.ENVIRONMENTS
    providers.gradleProperty("app.env").orNull?.let { return it.also { requireKnown(it) } }
    providers.environmentVariable("APP_ENV").orNull?.takeIf { it in envs }?.let { return it }

    val flavorPattern = Regex("(${envs.joinToString("|")})(Debug|Release)", RegexOption.IGNORE_CASE)
    val requested = gradle.startParameter.taskNames
        .mapNotNull { flavorPattern.find(it.substringAfterLast(':'))?.groupValues?.get(1)?.lowercase() }
        .toSet()
    if (requested.size > 1) {
        throw GradleException(
            "한 빌드에 여러 환경(${requested.joinToString()})을 섞을 수 없다. 환경별로 따로 실행하거나 -Papp.env=<env> 를 준다.",
        )
    }
    return requested.firstOrNull() ?: "dev"
}

private fun requireKnown(env: String) {
    if (env !in EnvConfig.ENVIRONMENTS) throw GradleException("알 수 없는 app.env: $env (가능: ${EnvConfig.ENVIRONMENTS.joinToString()})")
}
