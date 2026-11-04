package io.github.namjh654.papy.di

import io.github.namjh654.papy.core.data.di.dataModule
import io.github.namjh654.papy.core.data.logging.configureLogging
import io.github.namjh654.papy.core.data.platform.PlatformServices
import io.github.namjh654.papy.feature.auth.di.authModule
import io.github.namjh654.papy.feature.home.di.homeModule
import io.github.namjh654.papy.feature.example.di.exampleModule
import io.github.namjh654.papy.feature.settings.di.settingsModule
import io.github.namjh654.papy.feature.notice.di.noticeModule
import io.github.namjh654.papy.feature.permission.di.permissionModule
import io.github.namjh654.papy.feature.inbox.di.inboxModule
import io.github.namjh654.papy.feature.search.di.searchModule
import io.github.namjh654.papy.feature.chat.di.chatModule
import io.github.namjh654.papy.feature.community.di.communityModule
import io.github.namjh654.papy.feature.documents.DocumentEngine
import io.github.namjh654.papy.feature.documents.di.documentsModule
import org.koin.core.Koin
import org.koin.core.context.loadKoinModules
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

/** 앱 전체 Koin 모듈 목록. 새 feature 를 추가하면 그 feature 의 Koin 모듈을 한 줄 추가한다. */
fun appModules(
    services: PlatformServices = PlatformServices(),
): List<Module> = listOf(
    dataModule(services = services),
    authModule,
    homeModule,
    exampleModule,
    settingsModule,
    noticeModule,
    permissionModule,
    inboxModule,
    searchModule,
    chatModule,
    communityModule,
    documentsModule,
)

/**
 * Android/iOS 공통 Koin 진입점입니다. 플랫폼별 설정(androidContext 등)은 [appDeclaration]으로 전달합니다.
 * 환경(API URL 등)은 빌드가 생성한 AppConfig 가 정한다 — 여기로 넘기지 않는다 (DEC-058).
 * 로그(Kermit)도 여기서 설정한다: env log.* + 크래시 리포터.
 * @param services 플랫폼 SDK 구현(푸시·크래시·이벤트·리뷰). 설정 파일이 없으면 각각 NoOp
 */
fun initKoin(
    services: PlatformServices = PlatformServices(),
    appDeclaration: KoinAppDeclaration = {},
): Koin {
    configureLogging(services.crashReporter)
    return startKoin {
        appDeclaration()
        modules(appModules(services))
    }.koin
}

/**
 * 플랫폼 문서 엔진을 넣는다(initKoin 다음에 한 번). Android: Rust 엔진 + PdfRenderer, iOS: Swift 구현.
 * Swift: `IOSAppKoin.shared.registerDocumentEngine(engine: PapyDocumentEngine())`
 */
fun registerDocumentEngine(engine: DocumentEngine) {
    loadKoinModules(module { single<DocumentEngine> { engine } })
}
