package cloud.elsa.app.core.data.di

import cloud.elsa.app.core.data.config.AppConfig
import cloud.elsa.app.core.data.database.LocalCache
import cloud.elsa.app.core.data.database.LocalCacheCleaner
import cloud.elsa.app.core.data.flags.FeatureFlags
import cloud.elsa.app.core.data.files.DownloadDirectory
import cloud.elsa.app.core.data.files.FileDownloads
import cloud.elsa.app.core.data.files.FileTransport
import cloud.elsa.app.core.data.files.KtorFileTransport
import okio.FileSystem
import okio.Path.Companion.toPath
import org.koin.dsl.bind
import cloud.elsa.app.core.data.security.AppLock
import cloud.elsa.app.core.data.network.AccessTokenProvider
import cloud.elsa.app.core.data.network.ApiConfig
import cloud.elsa.app.core.data.network.BaseApi
import cloud.elsa.app.core.data.network.HttpTransport
import cloud.elsa.app.core.data.network.KtorClient
import cloud.elsa.app.core.data.network.UnauthorizedHandler
import cloud.elsa.app.core.data.network.createPlatformHttpClient
import cloud.elsa.app.core.data.session.AuthStore
import cloud.elsa.app.core.data.session.RemoteTokenRefreshApi
import cloud.elsa.app.core.data.session.TokenRefreshApi
import cloud.elsa.app.core.data.session.TokenRefresher
import cloud.elsa.app.core.data.settings.AppSettingsRepository
import cloud.elsa.app.core.data.session.SessionRepository
import cloud.elsa.app.core.data.session.UserStore
import cloud.elsa.app.core.data.storage.login.LoginStorage
import cloud.elsa.app.core.data.storage.setting.SettingStorage
import cloud.elsa.app.core.data.storage.token.TokenStorage
import cloud.elsa.app.core.data.storage.user.UserStorage
import cloud.elsa.app.core.data.push.PushManager
import cloud.elsa.app.core.data.push.PushProvider
import cloud.elsa.app.core.data.push.devicePlatform
import cloud.elsa.app.core.data.monitoring.AnalyticsTracker
import cloud.elsa.app.core.data.monitoring.CrashReporter
import cloud.elsa.app.core.data.platform.PlatformServices
import cloud.elsa.app.core.data.review.InAppReview
import cloud.elsa.app.core.data.update.AppUpdateManager
import cloud.elsa.app.core.data.update.AppVersionApi
import cloud.elsa.app.core.data.update.RemoteAppVersionApi
import cloud.elsa.app.core.data.push.PushRepository
import cloud.elsa.app.core.data.social.SocialAuthProvider
import cloud.elsa.app.core.data.social.SocialSessionCleaner
import cloud.elsa.app.core.data.push.PushTokenApi
import cloud.elsa.app.core.data.push.RemotePushTokenApi
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * core:data 의 Koin 모듈. 네트워크·저장소·세션·푸시를 한 곳에서 등록한다.
 * 플랫폼 구현(저장소 DataStore·Keystore / NSUserDefaults·Keychain, 네트워크 상태)은 [platformDataModule] 이 제공한다.
 */
fun dataModule(
    apiConfig: ApiConfig = ApiConfig(),
    services: PlatformServices = PlatformServices(),
): Module = module {
    includes(platformDataModule)

    // 앱 수명 작업(설정 읽기·푸시 등록). 한 작업의 실패가 다른 작업을 취소하지 않게 SupervisorJob
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // network
    single { apiConfig }
    single<HttpClient> { createPlatformHttpClient(apiConfig) }
    single<HttpTransport> { KtorClient(get()) }
    single<AccessTokenProvider> {
        val tokenStorage = get<TokenStorage>()
        AccessTokenProvider { tokenStorage.getAccessToken() }
    }
    // 401 → 재발급 → 재시도 (DEC-045). auth.refreshPath 를 비우면 재발급 없이 세션 만료
    if (AppConfig.AUTH_REFRESH_PATH.isNotBlank()) {
        single<TokenRefreshApi> {
            RemoteTokenRefreshApi(baseApi = BaseApi(config = get(), transport = get()), path = AppConfig.AUTH_REFRESH_PATH)
        }
    }
    single<UnauthorizedHandler> {
        // SessionRepository → PushRepository → PushTokenApi → BaseApi 순환을 피하려고 만료할 때 꺼낸다
        val koinScope = this
        TokenRefresher(
            tokenStorage = get(),
            api = getOrNull<TokenRefreshApi>(),
            expireSession = { koinScope.get<SessionRepository>().expire() },
        )
    }
    single {
        BaseApi(
            config = get(),
            transport = get(),
            tokenProvider = get(),
            unauthorizedHandler = get(),
        )
    }

    // storage (LocalStorage 래퍼)
    single { LoginStorage(localStorage = get()) }
    single { TokenStorage(secureStorage = get(), legacyStorage = get()) }
    single { UserStorage(localStorage = get()) }
    single { SettingStorage(localStorage = get()) }

    // 앱 잠금(26-1) — 루트 화면이 isLocked 를 보고 잠금 화면을 덮는다
    single { AppLock(storage = get(), authStore = get(), scope = appScope) }

    // 앱 설정(테마·언어) — 루트 화면이 관찰
    single { AppSettingsRepository(storage = get(), scope = appScope) }

    // session (여러 feature 가 공유)
    single { AuthStore() }
    single { UserStore() }
    single {
        val koinScope = this
        SessionRepository(
            tokenStorage = get(),
            userStorage = get(),
            loginStorage = get(),
            authStore = get(),
            userStore = get(),
            listeners = listOf(
                get<PushRepository>(),
                SocialSessionCleaner(socialAuth = get(), userStore = get()),
                // 기능 DB(LocalCache) 정리 — 기능 모듈이 등록한 것을 쓸 때 모은다
                LocalCacheCleaner(caches = { koinScope.getAll<LocalCache>() }, storage = get(), userStore = get()),
                // 로그아웃하면 앱 잠금을 끈다(26-1)
                get<AppLock>(),
            ),
        )
    }

    // 플랫폼 SDK 구현(설정 없으면 NoOp) — PlatformServices
    single<PushProvider> { services.pushProvider }
    single<CrashReporter> { services.crashReporter }
    single<AnalyticsTracker> { services.analytics }
    single<InAppReview> { services.inAppReview }
    single<SocialAuthProvider> { services.socialAuth }

    // 로그인 사용자 → 크래시·이벤트 사용자 id (앱 수명 동안 따라간다)
    single(createdAtStart = true) {
        val crash = get<CrashReporter>()
        val analytics = get<AnalyticsTracker>()
        appScope.launch {
            get<UserStore>().user.map { it?.id }.distinctUntilChanged().collect { id ->
                crash.setUserId(id)
                analytics.setUserId(id)
            }
        }
    }

    // 강제·선택 업데이트 (env update.checkPath 가 비면 확인 안 함)
    if (AppConfig.UPDATE_CHECK_PATH.isNotBlank()) {
        // 서버 없이 안내 화면을 볼 때: single<AppVersionApi> { SampleAppVersionApi() }
        single<AppVersionApi> { RemoteAppVersionApi(baseApi = get(), path = AppConfig.UPDATE_CHECK_PATH) }
    }
    single {
        AppUpdateManager(
            api = getOrNull<AppVersionApi>(),
            storage = get(),
            currentVersion = AppConfig.APP_VERSION_NAME,
            platform = devicePlatform,
            scope = appScope,
            featureFlags = get(),
        )
    }
    // 받은 파일 보관(30-1) — 로그아웃하면 LocalCache 로 지워진다
    single<FileTransport> { KtorFileTransport(httpClient = get(), apiBaseUrl = apiConfig.baseUrl, tokenProvider = get()) }
    single {
        FileDownloads(transport = get(), fileSystem = FileSystem.SYSTEM, directory = get<DownloadDirectory>().path.toPath(), storage = get())
    } bind LocalCache::class

    // 기능 켜고 끄기(28-2): 버전 확인 응답의 features, 마지막 값 저장
    single { FeatureFlags(storage = get(), scope = appScope) }

    // push
    single<PushTokenApi> { RemotePushTokenApi(baseApi = get()) }
    single { PushRepository(api = get(), storage = get(), provider = get(), authStore = get()) }
    single { PushManager(repository = get(), provider = get(), scope = appScope) }
}

/** 플랫폼별 LocalStorage(평문)·SecureStorage(Keystore/Keychain)·NetworkMonitor 구현 바인딩 */
internal expect val platformDataModule: Module
