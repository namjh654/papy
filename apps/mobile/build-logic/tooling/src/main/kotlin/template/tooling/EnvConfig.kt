package template.tooling

import java.io.File
import java.util.Properties

/**
 * 앱 환경(dev / staging / prod) 설정 (docs/environments.md, DEC-058).
 *
 * 원본 (뒤가 앞을 덮어쓴다)
 *   env/common.properties              모든 환경 공통 공개 값 (커밋)
 *   env/<env>.properties               환경별 공개 값 (커밋)
 *   env/<env>.local.properties         비밀 값 (.gitignore) — 키 목록은 env/<env>.local.properties.example (커밋)
 *
 * 비밀 키는 .example 에 있으면 값이 없어도 빈 문자열로 생성되어 어느 환경이든 컴파일된다.
 * 모든 환경의 키 집합은 같아야 한다(한 환경에만 있는 키 = 다른 환경 빌드가 깨짐) → [consistencyErrors].
 */
class EnvConfig(private val repo: Repo) {

    /** 환경의 최종 값 (키 정렬) */
    fun values(env: String): Map<String, String> {
        require(env in ENVIRONMENTS) { "알 수 없는 환경: $env (가능: ${ENVIRONMENTS.joinToString()})" }
        val merged = sortedMapOf<String, String>()
        merged += load(COMMON)
        merged += load("env/$env.properties")
        // .example 은 키 목록만 쓴다(예시 값은 쓰지 않음) — 실제 값은 local 에서만
        load("env/$env.local.properties.example").keys.forEach { merged.putIfAbsent(it, "") }
        merged += load("env/$env.local.properties")
        return merged
    }

    /** 환경별 입력 파일 (Gradle 입력 선언용, 없는 파일 포함) */
    fun inputFiles(env: String): List<File> = listOf(
        COMMON, "env/$env.properties", "env/$env.local.properties.example", "env/$env.local.properties",
    ).map(repo::file)

    fun consistencyErrors(): List<String> {
        val keys = ENVIRONMENTS.associateWith { values(it).keys }
        val all = keys.values.flatten().toSortedSet()
        val errors = all.mapNotNull { key ->
            val missing = ENVIRONMENTS.filter { key !in keys.getValue(it) }
            if (missing.isEmpty()) null else "env: '$key' 가 ${missing.joinToString()} 에 없음 (모든 환경에 같은 키를 둔다)"
        }.toMutableList()
        REQUIRED.forEach { key -> if (key !in all) errors += "env: 필수 키 '$key' 없음 (env/common.properties 또는 env/<env>.properties)" }
        return errors
    }

    /** core:data 의 AppConfig (Kotlin 상수) */
    fun kotlinAppConfig(env: String): String {
        val values = values(env)
        val out = mutableListOf(
            "// 자동 생성 — 수정 금지. 원본: env/common.properties + env/$env.properties (+ env/$env.local.properties)",
            "package ${repo.basePackage}.core.data.config",
            "",
            "/**",
            " * 빌드 환경 설정. 값은 빌드할 때 정해진다 — 코드는 환경 이름으로 분기하지 말고 값(URL·플래그)만 쓴다.",
            " * 환경 선택: -Papp.env=<dev|staging|prod> > Xcode APP_ENV > Android 태스크의 flavor > dev",
            " */",
            "object AppConfig {",
            "    /** 표시·진단용 (분기 금지) */",
            "    const val ENVIRONMENT: String = \"$env\"",
        )
        values.forEach { (key, raw) -> out += "    const val ${constName(key)}${literal(raw)}" }
        out += listOf("}", "")
        return out.joinToString("\n")
    }

    /** iOS: 환경별 Build Configuration(Debug-<env>, Release-<env>)에 값을 나눠 준다 */
    fun iosXcconfig(): String {
        val out = mutableListOf(
            "// 자동 생성 — 수정 금지. 원본: env/*.properties · 생성: ./gradlew generateEnvConfig",
            "// Build Configuration 이름: Debug-<env> / Release-<env> (env = ${ENVIRONMENTS.joinToString("·")})",
            "",
        )
        for (env in ENVIRONMENTS) {
            val v = values(env)
            out += "APP_ENV[config=*-$env] = $env"
            out += "APP_DISPLAY_NAME[config=*-$env] = ${displayName(v)}"
            out += "BUNDLE_ID_SUFFIX[config=*-$env] = ${v["app.idSuffix"].orEmpty()}"
            out += "MARKETING_VERSION[config=*-$env] = ${v["app.versionName"].orEmpty()}"
            out += "CURRENT_PROJECT_VERSION[config=*-$env] = ${v["app.versionCode"].orEmpty()}"
            out += ""
        }
        out += "// Kotlin framework 빌드 타입 (embedAndSignAppleFrameworkForXcode)"
        out += "KOTLIN_FRAMEWORK_BUILD_TYPE[config=Debug-*] = debug"
        out += "KOTLIN_FRAMEWORK_BUILD_TYPE[config=Release-*] = release"
        out += ""
        return out.joinToString("\n")
    }

    /**
     * iOS 소셜 로그인 URL 스킴(DEC-119) — 키가 비밀 파일(local)에만 있으므로 **커밋하지 않는** 파일로 만든다.
     * 키가 없는 제공자는 쓰이지 않는 자리 값(`elsa-social-off-…`)이라 맨 `kakao` 같은 스킴을 등록하지 않는다.
     * Config.xcconfig 가 기본값을 두고 이 파일을 `#include?` 한다(없어도 빌드된다).
     */
    fun iosSocialXcconfig(): String {
        val out = mutableListOf(
            "// 자동 생성 — 수정 금지, 커밋하지 않음(.gitignore). 원본: env/<env>.local.properties · 생성: ./gradlew generateEnvConfig",
            "",
        )
        for (env in ENVIRONMENTS) {
            val v = values(env)
            val kakao = v["social.kakao.appKey"].orEmpty()
            val google = v["social.google.iosClientId"].orEmpty()
            out += "SOCIAL_KAKAO_URL_SCHEME[config=*-$env] = ${if (kakao.isBlank()) "elsa-social-off-kakao" else "kakao$kakao"}"
            out += "SOCIAL_GOOGLE_URL_SCHEME[config=*-$env] = ${if (google.isBlank()) "elsa-social-off-google" else reversedGoogleClientId(google)}"
            out += ""
        }
        return out.joinToString("\n")
    }

    private fun load(path: String): Map<String, String> {
        val f = repo.file(path)
        if (!f.isFile) return emptyMap()
        val p = Properties()
        f.reader(Charsets.UTF_8).use(p::load)
        return p.stringPropertyNames().associateWith { p.getProperty(it).trim() }
    }

    companion object {
        val ENVIRONMENTS = listOf("dev", "staging", "prod")
        const val COMMON = "env/common.properties"
        const val IOS_XCCONFIG = "iosApp/Configuration/Env.generated.xcconfig"
        const val IOS_SOCIAL_XCCONFIG = "iosApp/Configuration/Social.local.generated.xcconfig"

        /** 1234-abc.apps.googleusercontent.com → com.googleusercontent.apps.1234-abc (GoogleSignIn 의 URL 스킴) */
        fun reversedGoogleClientId(clientId: String): String =
            "com.googleusercontent.apps." + clientId.removeSuffix(".apps.googleusercontent.com")
        val REQUIRED = listOf("app.name", "app.idSuffix", "app.nameSuffix", "app.versionName", "app.versionCode", "api.baseUrl", "network.log")

        /** 앱 이름 + 환경 접미사 (예: "ELSA Dev") */
        fun displayName(values: Map<String, String>): String =
            listOf(values["app.name"].orEmpty(), values["app.nameSuffix"].orEmpty()).filter { it.isNotBlank() }.joinToString(" ")

        /** api.baseUrl → API_BASE_URL, feature.newHome → FEATURE_NEW_HOME */
        fun constName(key: String): String =
            key.split('.', '-', '_').joinToString("_") { part -> part.replace(Regex("(?<=[a-z0-9])(?=[A-Z])"), "_").uppercase() }

        /** 타입 추론: true/false → Boolean, 정수 → Int/Long, 그 밖 → String */
        private fun literal(raw: String): String = when {
            raw == "true" || raw == "false" -> ": Boolean = $raw"
            Regex("-?\\d{1,9}").matches(raw) -> ": Int = $raw"
            Regex("-?\\d{10,18}").matches(raw) -> ": Long = ${raw}L"
            else -> ": String = \"" + raw.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$") + "\""
        }
    }
}
