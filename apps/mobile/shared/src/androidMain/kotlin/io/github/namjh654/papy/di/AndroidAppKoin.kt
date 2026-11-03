package io.github.namjh654.papy.di

import android.content.Context
import io.github.namjh654.papy.core.data.platform.PlatformServices
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module

/**
 * Android Koin 진입점입니다. Application.onCreate에서 한 번만 호출합니다.
 *
 * @param services 플랫폼 SDK 구현(FCM·Crashlytics·Analytics·Play 리뷰). 설정이 없으면 각각 NoOp
 * @param appModules androidApp 전용 모듈(화면 전용 의존성 등)
 */
fun initAndroidKoin(
    context: Context,
    services: PlatformServices = PlatformServices(),
    appModules: List<Module> = emptyList(),
) {
    initKoin(services) {
        androidContext(context.applicationContext)
        modules(appModules)
    }
}
