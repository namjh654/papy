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

    // Papy 엔진(Rust, UniFFI) — Android 는 aar(네이티브 libjnidispatch 포함)
    implementation(libs.jna) { artifact { type = "aar" } }

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
        // Papy 엔진(libpapy_ffi.so)은 64비트 ARM·x86 만 만든다(scripts/build-mobile-engine.mjs).
        // 다른 ABI(32비트 ARM 등) 기기에 설치되면 엔진이 없어 문서를 열 수 없으므로 막고, JNA 의 다른 ABI 라이브러리도 뺀다.
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
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
