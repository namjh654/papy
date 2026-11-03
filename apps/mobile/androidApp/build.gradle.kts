import org.jetbrains.kotlin.gradle.dsl.JvmTarget
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler) // Kotlin 2.x 전용 Compose 컴파일러
    alias(libs.plugins.kotlinSerialization) // Navigation type-safe route
    // 환경 flavor dev/staging/prod — 앱 이름·ID 접미사, prod 릴리스 R8, 서명 (docs/environments.md)
    id("papy.android.environments")
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

dependencies {
    implementation(projects.shared)

    // AndroidX Activity & Lifecycle Compose 바인딩
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.androidx.lifecycle.viewmodelCompose)

    // Android Native Navigation
    implementation(libs.androidx.navigation.compose)

    // Android Native Jetpack Compose (BOM 사용으로 버전 자동 맞춤)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)

    // Design System: 이미지 로딩(선택한 사진 미리보기)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Push (FCM). google-services.json 이 없어도 빌드되고 푸시만 비활성
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)

    // 소셜 로그인 SDK (social/, DEC-106·119) — 키가 없으면 초기화하지 않고 버튼도 보이지 않는다
    implementation(libs.kakao.user)
    implementation(libs.naver.oauth)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)

    // Play 인앱 리뷰·업데이트 (review/, update/ — 인터페이스 뒤)
    implementation(libs.play.review)
    implementation(libs.play.app.update)
    // 앱 잠금: 생체 인증·기기 암호(26-1, DEC-134) — MainActivity 는 FragmentActivity
    implementation(libs.androidx.biometric)

    // Koin Android & Compose
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    debugImplementation(libs.compose.ui.tooling)
}

android {
    namespace = "io.github.namjh654.papy"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    buildFeatures {
        buildConfig = true
        compose = true // Jetpack Compose 활성화
    }

    defaultConfig {
        applicationId = "io.github.namjh654.papy"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        // versionCode·versionName 은 env/common.properties (papy.android.environments 가 flavor 에 넣는다)
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// Firebase 설정 파일(flavor 별 src/<env>/google-services.json 또는 공통 google-services.json)이 있을 때만
// google-services 플러그인을 적용한다. 파일이 없는 flavor 는 경고만 내고 푸시가 NoOp 이 된다 (docs/push.md)
val googleServicesFiles = listOf(file("google-services.json")) +
    listOf("dev", "staging", "prod").map { file("src/$it/google-services.json") }
if (googleServicesFiles.any { it.exists() }) {
    apply(plugin = "com.google.gms.google-services")
    // 크래시 리포트용 R8 매핑 업로드(release). Firebase 설정이 없으면 적용하지 않는다
    apply(plugin = "com.google.firebase.crashlytics")
    extensions.configure<com.google.gms.googleservices.GoogleServicesPlugin.GoogleServicesPluginConfig> {
        missingGoogleServicesStrategy = com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy.WARN
    }
} else {
    logger.lifecycle("androidApp: google-services.json 없음 → 푸시·크래시·이벤트 비활성(NoOp)으로 빌드")
}
