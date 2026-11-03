import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import java.util.Properties
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import template.tooling.EnvConfig
import template.tooling.Repo

/**
 * `papy.android.environments` — androidApp 의 환경 flavor(dev / staging / prod) (DEC-058, docs/environments.md).
 *
 * - 버전: env 의 app.versionName·app.versionCode (한 곳에서 관리, docs/release.md). dev·staging 은 versionName 에 -<env>
 * - applicationId 접미사(env 의 app.idSuffix: .dev, .staging)와 앱 이름(app.name + app.nameSuffix)
 *   → 세 앱을 한 기기에 함께 설치할 수 있다
 * - google-services.json 은 flavor 별 `src/<env>/` (또는 모듈 루트 공통). 없으면 그 flavor 는 푸시만 NoOp
 * - R8·리소스 축소·로그 제거(proguard-rules.pro)는 prodRelease 에만
 * - 릴리스 서명은 `env/release-signing.local.properties` 또는 환경변수(ANDROID_KEYSTORE_FILE 등)가 있을 때만
 * - 이번 빌드의 환경(appEnvironment)과 다른 flavor 를 빌드하려 하면 실패한다(KMP AppConfig 는 한 환경만 가짐)
 */
class AndroidEnvironmentsPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        val env = appEnvironment()
        val config = EnvConfig(Repo(rootDir))
        val signing = releaseSigning()

        extensions.configure<ApplicationExtension> {
            buildFeatures.resValues = true // flavor 별 app_name
            flavorDimensions += DIMENSION
            productFlavors {
                EnvConfig.ENVIRONMENTS.forEach { name ->
                    create(name) {
                        dimension = DIMENSION
                        val values = config.values(name)
                        values["app.idSuffix"]?.takeIf { it.isNotBlank() }?.let { applicationIdSuffix = it }
                        versionName = values.getValue("app.versionName")
                        versionCode = values.getValue("app.versionCode").toInt()
                        if (name != "prod") versionNameSuffix = "-$name"
                        resValue("string", "app_name", EnvConfig.displayName(values))
                        // 카카오 로그인 리다이렉트(kakao{앱 키}://oauth) — 키가 없으면 받는 Activity 를 끈다(DEC-119)
                        val kakaoKey = values["social.kakao.appKey"].orEmpty()
                        manifestPlaceholders["kakaoScheme"] = "kakao$kakaoKey"
                        manifestPlaceholders["kakaoEnabled"] = kakaoKey.isNotBlank().toString()
                    }
                }
            }
            if (signing != null) {
                signingConfigs.create("release") {
                    storeFile = file(signing.getValue("storeFile"))
                    storePassword = signing.getValue("storePassword")
                    keyAlias = signing.getValue("keyAlias")
                    keyPassword = signing.getValue("keyPassword")
                }
            }
            buildTypes.getByName("release") {
                proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
                if (signing != null) signingConfig = signingConfigs.getByName("release")
            }
        }

        extensions.configure<ApplicationAndroidComponentsExtension> {
            beforeVariants { variant ->
                val prodRelease = variant.buildType == "release" && variant.flavorName == "prod"
                variant.isMinifyEnabled = prodRelease
                variant.shrinkResources = prodRelease
            }
        }

        // 환경 불일치 방지: 이번 빌드의 AppConfig 환경과 다른 flavor 의 빌드를 막는다
        val pre = Regex("pre(${EnvConfig.ENVIRONMENTS.joinToString("|") { it.replaceFirstChar(Char::uppercase) }})(Debug|Release)Build")
        tasks.configureEach {
            val flavor = pre.matchEntire(name)?.groupValues?.get(1)?.lowercase() ?: return@configureEach
            if (flavor != env) {
                val message = "이 빌드의 환경은 '$env' 인데 '$flavor' flavor 를 빌드하려 한다. " +
                    "-Papp.env=$flavor 로 실행하거나 환경별로 따로 빌드한다 (docs/environments.md)."
                doFirst { throw GradleException(message) }
            }
        }
        Unit
    }

    /** 서명 값: env/release-signing.local.properties → 환경변수. 하나라도 없으면 null(서명 없이 빌드) */
    private fun Project.releaseSigning(): Map<String, String>? {
        val file = rootProject.file("env/release-signing.local.properties")
        val props = Properties().apply { if (file.isFile) file.reader().use(::load) }
        val keys = mapOf(
            "storeFile" to "ANDROID_KEYSTORE_FILE",
            "storePassword" to "ANDROID_KEYSTORE_PASSWORD",
            "keyAlias" to "ANDROID_KEY_ALIAS",
            "keyPassword" to "ANDROID_KEY_PASSWORD",
        )
        val values = keys.mapValues { (key, envVar) -> props.getProperty(key) ?: providers.environmentVariable(envVar).orNull }
        return if (values.values.all { !it.isNullOrBlank() }) values.mapValues { it.value!! } else null
    }

    private companion object {
        const val DIMENSION = "environment"
    }
}
