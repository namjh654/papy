package cloud.elsa.app.core.data.platform

import cloud.elsa.app.core.data.monitoring.AnalyticsTracker
import cloud.elsa.app.core.data.monitoring.CrashReporter
import cloud.elsa.app.core.data.monitoring.NoOpAnalyticsTracker
import cloud.elsa.app.core.data.monitoring.NoOpCrashReporter
import cloud.elsa.app.core.data.push.NoOpPushProvider
import cloud.elsa.app.core.data.push.PushProvider
import cloud.elsa.app.core.data.review.InAppReview
import cloud.elsa.app.core.data.review.NoOpInAppReview
import cloud.elsa.app.core.data.social.NoOpSocialAuthProvider
import cloud.elsa.app.core.data.social.SocialAuthProvider

/**
 * 플랫폼이 KMP 에 넘기는 SDK 구현 묶음. 모두 설정이 없으면 NoOp — 빌드·실행은 같다.
 * - Android: `initAndroidKoin(context, PlatformServices(pushProvider = …, crashReporter = …))`
 * - iOS: `IOSAppKoin.shared.initialize(services: PlatformServices(pushProvider:crashReporter:analytics:inAppReview:socialAuth:))`
 */
data class PlatformServices(
    val pushProvider: PushProvider = NoOpPushProvider,
    val crashReporter: CrashReporter = NoOpCrashReporter,
    val analytics: AnalyticsTracker = NoOpAnalyticsTracker,
    val inAppReview: InAppReview = NoOpInAppReview,
    /** 소셜 SDK 세션 정리(DEC-107) */
    val socialAuth: SocialAuthProvider = NoOpSocialAuthProvider,
)
