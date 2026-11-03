package io.github.namjh654.papy.app

import android.app.Application
import io.github.namjh654.papy.di.initAndroidKoin
import io.github.namjh654.papy.core.data.platform.PlatformServices
import io.github.namjh654.papy.core.data.settings.AppSettingsRepository
import io.github.namjh654.papy.core.data.push.PushManager
import io.github.namjh654.papy.monitoring.FirebaseAnalyticsTracker
import io.github.namjh654.papy.monitoring.FirebaseCrashReporter
import io.github.namjh654.papy.review.PlayInAppReview
import io.github.namjh654.papy.push.FcmPushProvider
import io.github.namjh654.papy.push.NotificationChannels
import io.github.namjh654.papy.social.AndroidSocialAuthProvider
import io.github.namjh654.papy.social.AndroidSocialSdk
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext

class PapyApplication : Application() {

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
