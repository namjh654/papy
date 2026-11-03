import co.touchlab.skie.configuration.DefaultArgumentInterop
import co.touchlab.skie.configuration.FlowInterop

// umbrella: 앱 진입점(Koin 초기화) + iOS framework "Core".
// 새 feature 모듈은 아래 exported 목록과 AppKoin.appModules 에 한 줄씩 추가한다.
plugins {
    id("elsa.kmp.library")
    alias(libs.plugins.skie)
}

val exported = listOf(
    ":core:common",
    ":core:data",
    ":feature:auth",
    ":feature:home",
    ":feature:example",
    ":feature:settings",
    ":feature:notice",
    ":feature:permission",
    ":feature:inbox",
    ":feature:search",
    ":feature:chat",
    ":feature:community",
)

skie {
    features {
        group {
            FlowInterop.Enabled(true)
            enableSwiftUIObservingPreview = true
            DefaultArgumentInterop.Enabled(false)
        }
    }
}

kotlin {
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.framework {
            // 이름을 바꾸면 모든 Swift `import Core` 를 바꿔야 한다 (DEC-034)
            baseName = "Core"
            // 번들 ID 를 정해 두지 않으면 Kotlin 이 추측하며 경고한다 (renameApp 이 패키지와 함께 바꾼다)
            binaryOption("bundleId", "cloud.elsa.app.Core")
            isStatic = false
            // 로컬 DB(SQLDelight native driver, DEC-130)는 시스템 sqlite 를 쓴다
            linkerOpts("-lsqlite3")
            exported.forEach { export(project(it)) }
        }
    }

    sourceSets {
        commonMain.dependencies {
            exported.forEach { api(project(it)) }
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
        }
    }
}
