// Android UI 흐름 테스트 — 설치된 dev 앱을 밖에서(UiAutomator) 조작한다. iOS 의 iosApp/UITests 와 같은 흐름.
// 실행: scripts/orca.sh uitest android  (목업 서버를 띄우고 에뮬레이터·기기에서 돌린다)
import java.util.Properties

plugins {
    alias(libs.plugins.androidTest)
}

/** 대상 앱 패키지 = androidApp 의 applicationId + env/dev.properties 의 app.idSuffix (renameApp 뒤에도 맞는다) */
val appPackage: String = run {
    val base = Regex("""applicationId\s*=\s*"([^"]+)"""")
        .find(rootProject.file("androidApp/build.gradle.kts").readText())?.groupValues?.get(1) ?: "io.github.namjh654.papy"
    val env = Properties().apply { rootProject.file("env/dev.properties").reader(Charsets.UTF_8).use { load(it) } }
    base + env.getProperty("app.idSuffix", "")
}

android {
    namespace = "io.github.namjh654.papy.uitest"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    // 앱 프로세스 밖에서 돈다(앱 데이터 삭제·재실행·시스템 권한 창을 다룰 수 있다)
    targetProjectPath = ":androidApp"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["appPackage"] = appPackage
        // 대상 앱은 dev flavor 로 빌드·설치한다
        missingDimensionStrategy("environment", "dev")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
}
