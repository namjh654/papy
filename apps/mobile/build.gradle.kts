plugins {
    // 템플릿 점검·생성 태스크: templateCheck, generateDesignTokens (build-logic)
    id("elsa.template")

    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidTest) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.googleServices) apply false // androidApp/google-services.json 이 있을 때만 적용
    alias(libs.plugins.firebaseCrashlytics) apply false // 위와 같이 설정 파일이 있을 때만 (release 매핑 업로드)
    alias(libs.plugins.sqldelight) apply false // DB 가 있는 기능 모듈만 (docs/local-db.md)

    // 커버리지: 모든 KMP 모듈(elsa.kmp.library 가 적용)을 루트에서 합친다 → ./gradlew koverHtmlReport
    alias(libs.plugins.kover)
}

dependencies {
    // KMP 모듈 전부(= :core:*, :feature:*, :shared) — 새 feature 는 자동 포함
    subprojects.filter { it.path.startsWith(":core:") || it.path.startsWith(":feature:") }
        .forEach { kover(project(it.path)) }
}