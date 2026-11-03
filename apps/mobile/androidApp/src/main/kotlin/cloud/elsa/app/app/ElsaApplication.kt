package cloud.elsa.app.app

import android.app.Application
import cloud.elsa.app.di.initAndroidKoin
import cloud.elsa.app.core.data.platform.PlatformServices
import cloud.elsa.app.core.data.settings.AppSettingsRepository
import cloud.elsa.app.core.data.push.PushManager
import cloud.elsa.app.monitoring.FirebaseAnalyticsTracker
import cloud.elsa.app.monitoring.FirebaseCrashReporter
import cloud.elsa.app.review.PlayInAppReview
import cloud.elsa.app.push.FcmPushProvider
import cloud.elsa.app.push.NotificationChannels
import cloud.elsa.app.social.AndroidSocialAuthProvider
import cloud.elsa.app.social.AndroidSocialSdk
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext

class ElsaApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Activity 재생성(회전 등)과 무관하게 Koin 은 프로세스당 한 번만 시작한다.
        // 환경(API URL 등)은 flavor 에 맞춰 빌드가 생성한 AppConfig 가 정한다 (docs/environments.md).
        // 푸시: google-services.json 이 있으면 FCM, 없으면 비활성(NoOp) — 빌드·실행은 같다
        NotificationChannels.create(this)
        CurrentActivity.register(this)
        // 소셜 SDK: env 에 키가 있는 제공자만 초기화(DEC-119)
        AndroidSocialSdk.initialize(this)

        // Firebase 설정(google-services.json)이 없으면 푸시·크래시·이벤트는 각각 NoOp
        val pushProvider = FcmPushProvider.createOrNoOp(this)
        initAndroidKoin(
            context = this,
            services = PlatformServices(
                pushProvider = pushProvider,
                crashReporter = FirebaseCrashReporter.createOrNoOp(this),
                analytics = FirebaseAnalyticsTracker.createOrNoOp(this),
                inAppReview = PlayInAppReview(this),
                socialAuth = AndroidSocialAuthProvider(this),
            ),
        )

        (pushProvider as? FcmPushProvider)?.start(GlobalContext.get().get<PushManager>())

        // Android 12+ 시작 화면(스플래시 창)을 저장된 테마에 맞춘다 — 저장값이 없으면 시스템(AUTO)
        val settings = GlobalContext.get().get<AppSettingsRepository>()
        runBlocking { settings.ensureLoaded() }
        syncAppNightMode(settings.theme.value)
    }
}
