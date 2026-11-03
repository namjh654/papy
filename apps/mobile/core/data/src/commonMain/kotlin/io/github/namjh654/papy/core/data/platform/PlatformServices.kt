package io.github.namjh654.papy.core.data.platform

import io.github.namjh654.papy.core.data.monitoring.AnalyticsTracker
import io.github.namjh654.papy.core.data.monitoring.CrashReporter
import io.github.namjh654.papy.core.data.monitoring.NoOpAnalyticsTracker
import io.github.namjh654.papy.core.data.monitoring.NoOpCrashReporter
import io.github.namjh654.papy.core.data.push.NoOpPushProvider
import io.github.namjh654.papy.core.data.push.PushProvider
import io.github.namjh654.papy.core.data.review.InAppReview
import io.github.namjh654.papy.core.data.review.NoOpInAppReview
import io.github.namjh654.papy.core.data.social.NoOpSocialAuthProvider
import io.github.namjh654.papy.core.data.social.SocialAuthProvider

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
