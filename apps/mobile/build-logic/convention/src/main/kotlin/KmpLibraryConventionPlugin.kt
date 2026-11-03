import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * `papy.kmp.library` — 모든 KMP 모듈의 공통 설정.
 *
 * - 타깃: Android(KMP Android 라이브러리) + iosArm64 + iosSimulatorArm64
 * - Android: namespace 는 Gradle 경로에서 자동(`:core:data` → `io.github.namjh654.papy.core.data`), compile/minSdk 는 catalog, JVM 11
 * - 테스트: commonTest 에 kotlin-test + coroutines-test + Turbine, Android host test 활성, Kover(커버리지)
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("org.jetbrains.kotlin.multiplatform")
            apply("com.android.kotlin.multiplatform.library")
            apply("org.jetbrains.kotlin.plugin.serialization")
            apply("org.jetbrains.kotlinx.kover")
        }

        extensions.configure<KotlinMultiplatformExtension> {
            iosArm64()
            iosSimulatorArm64()

            (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget>("androidLibrary") {
                namespace = papyNamespace
                compileSdk = libs.int("android-compileSdk")
                minSdk = libs.int("android-minSdk")
                compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
                withHostTest {}
            }

            sourceSets.getByName("commonMain").dependencies {
                implementation(libs.lib("coroutines-core"))
            }
            sourceSets.getByName("commonTest").dependencies {
                implementation(libs.lib("kotlin-test"))
                implementation(libs.lib("coroutines-test"))
                implementation(libs.lib("turbine"))
            }
        }
    }
}
