// 모듈 공통 빌드 설정(convention plugin). 루트 settings 의 pluginManagement 에서 includeBuild 한다.
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") { from(files("../gradle/libs.versions.toml")) }
    }
}

rootProject.name = "build-logic"
include(":convention")
// 템플릿 도구 — 루트 빌드 밖에서 단독 실행: ./gradlew -p build-logic :tooling:run --args="…"
include(":tooling")
