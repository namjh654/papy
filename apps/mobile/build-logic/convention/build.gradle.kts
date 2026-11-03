plugins {
    `kotlin-dsl`
}

group = "io.github.namjh654.papy.buildlogic"

dependencies {
    // 플러그인 버전은 루트 build.gradle.kts 의 `apply false` 가 정한다(여기서는 컴파일용 API 만).
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.sqldelight.gradlePlugin)
    // 템플릿 점검·생성 로직 (JDK 만으로 도는 Kotlin 라이브러리)
    implementation(project(":tooling"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = "papy.kmp.library"
            implementationClass = "KmpLibraryConventionPlugin"
        }
        register("template") {
            id = "papy.template"
            implementationClass = "TemplateChecksPlugin"
        }
        register("appConfig") {
            id = "papy.appconfig"
            implementationClass = "AppConfigPlugin"
        }
        register("androidEnvironments") {
            id = "papy.android.environments"
            implementationClass = "AndroidEnvironmentsPlugin"
        }
        register("kmpFeature") {
            id = "papy.kmp.feature"
            implementationClass = "KmpFeatureConventionPlugin"
        }
        register("kmpDatabase") {
            id = "papy.kmp.database"
            implementationClass = "KmpDatabaseConventionPlugin"
        }
    }
}
