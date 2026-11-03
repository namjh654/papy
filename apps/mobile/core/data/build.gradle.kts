// 데이터 계층: network(Ktor·BaseApi) · storage(LocalStorage·도메인 저장소) · serialization(AppJson) · session
plugins {
    id("papy.kmp.library")
    // env/<env>.properties → commonMain AppConfig 상수 (docs/environments.md)
    id("papy.appconfig")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:common"))
            implementation(libs.koin.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.logging)
            // 로컬 DB(DEC-130): 기능 모듈이 생성 코드에서 쓰도록 api
            api(libs.sqldelight.runtime)
            // 받은 파일 보관(30-1, files/) — Android·iOS 같은 파일 API
            implementation(libs.okio)
        }
        commonTest.dependencies {
            implementation(libs.okio.fakefilesystem)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            // 권한 창(ActivityResult) — permission/AndroidPermissionController
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.datastore.core)
            implementation(libs.androidx.datastore.preferences.core)
            implementation(libs.sqldelight.android.driver)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.native.driver)
        }
    }
}
