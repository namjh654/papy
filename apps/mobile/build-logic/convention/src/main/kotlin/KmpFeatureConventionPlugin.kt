import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * `elsa.kmp.feature` — feature 모듈(`:feature:*`) 공통 설정.
 *
 * `elsa.kmp.library` + core:common(에러·ViewModel 베이스) + core:data(API·저장소·세션) + Koin.
 * feature 끼리는 의존하지 않는다. 공유가 필요한 것은 core:data 로 올린다.
 */
class KmpFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("elsa.kmp.library")

        extensions.configure<KotlinMultiplatformExtension> {
            sourceSets.getByName("commonMain").dependencies {
                api(project(":core:common"))
                api(project(":core:data"))
                implementation(libs.lib("koin-core"))
                implementation(libs.lib("kotlinx-serialization-json"))
            }
        }
    }
}
