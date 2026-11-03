// 템플릿 도구(JDK 만 필요): 디자인 토큰 생성, 문자열·아키텍처 점검, newFeature, renameApp.
// - 점검·생성 로직은 convention 의 루트 태스크(templateCheck 등)가 라이브러리로 쓴다
// - newFeature·renameApp 은 settings·패키지 폴더를 바꾸므로 루트 빌드 밖에서 실행한다:
//     ./gradlew -p build-logic :tooling:run --args="newFeature notice"
plugins {
    `embedded-kotlin`
    application
}

dependencies {
    implementation(embeddedKotlin("stdlib"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}

application {
    mainClass.set("template.tooling.MainKt")
}

tasks.named<JavaExec>("run") {
    // 저장소 루트에서 실행 (build-logic 의 상위)
    workingDir = rootDir.parentFile
}
