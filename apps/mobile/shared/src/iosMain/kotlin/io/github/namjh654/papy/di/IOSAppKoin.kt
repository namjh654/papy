package io.github.namjh654.papy.di

import io.github.namjh654.papy.core.data.platform.PlatformServices

object IOSAppKoin {

    /** iOS Koin 진입점(플랫폼 SDK 없이). App.init 에서 한 번만 호출한다. 환경은 빌드 구성(xcconfig APP_ENV → AppConfig)이 정한다. */
    fun initialize() {
        initialize(PlatformServices())
    }

    /**
     * 플랫폼 SDK 구현과 함께 초기화한다.
     * Swift: `IOSAppKoin.shared.initialize(services: PlatformServices(pushProvider:crashReporter:analytics:inAppReview:))`
     * 설정 파일이 없으면 각 자리에 `NoOpPushProvider.shared` 등 NoOp 을 넘긴다(PlatformSetup.makeServices()).
     */
    fun initialize(services: PlatformServices) {
        initKoin(services = services)
    }
}
