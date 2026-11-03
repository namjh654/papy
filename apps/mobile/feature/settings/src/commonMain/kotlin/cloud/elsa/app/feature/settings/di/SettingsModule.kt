package cloud.elsa.app.feature.settings.di

import cloud.elsa.app.core.data.config.AppConfig
import cloud.elsa.app.core.data.push.devicePlatform
import cloud.elsa.app.feature.settings.SettingsViewModel
import cloud.elsa.app.feature.settings.downloads.DownloadsViewModel
import cloud.elsa.app.feature.settings.inquiry.InquiryApi
import cloud.elsa.app.feature.settings.inquiry.InquiryRepository
import cloud.elsa.app.feature.settings.inquiry.InquiryViewModel
import cloud.elsa.app.feature.settings.inquiry.RemoteInquiryApi
import cloud.elsa.app.feature.settings.notification.NotificationSettingsApi
import cloud.elsa.app.feature.settings.notification.NotificationSettingsRepository
import cloud.elsa.app.feature.settings.notification.NotificationSettingsViewModel
import cloud.elsa.app.feature.settings.notification.RemoteNotificationSettingsApi
import cloud.elsa.app.feature.settings.profile.ProfileApi
import cloud.elsa.app.feature.settings.profile.ProfileRepository
import cloud.elsa.app.feature.settings.profile.ProfileViewModel
import cloud.elsa.app.feature.settings.profile.RemoteProfileApi
import cloud.elsa.app.feature.settings.withdraw.RemoteWithdrawApi
import cloud.elsa.app.feature.settings.withdraw.WithdrawApi
import cloud.elsa.app.feature.settings.withdraw.WithdrawRepository
import cloud.elsa.app.feature.settings.withdraw.WithdrawViewModel
import org.koin.dsl.module

/** feature:settings 의 Koin 모듈 */
val settingsModule = module {
    factory {
        SettingsViewModel(
            appSettings = get(), sessionRepository = get(), userStore = get(), inAppReview = get(), analytics = get(), appLock = get(),
        )
    }

    // 프로필 편집
    single<ProfileApi> { RemoteProfileApi(baseApi = get()) }
    single { ProfileRepository(api = get(), sessionRepository = get()) }
    factory { ProfileViewModel(userStore = get(), repository = get()) }

    // 회원 탈퇴
    single<WithdrawApi> { RemoteWithdrawApi(baseApi = get()) }
    single { WithdrawRepository(api = get(), sessionRepository = get(), loginStorage = get(), userStore = get(), socialAuth = get()) }
    factory { WithdrawViewModel(repository = get(), userStore = get()) }

    // 문의하기(25-3)
    single<InquiryApi> { RemoteInquiryApi(baseApi = get()) }
    single { InquiryRepository(api = get(), appVersion = AppConfig.APP_VERSION_NAME, platform = devicePlatform) }
    factory { InquiryViewModel(repository = get(), userStore = get()) }

    // 알림 설정(26-2)
    single<NotificationSettingsApi> { RemoteNotificationSettingsApi(baseApi = get()) }
    single { NotificationSettingsRepository(api = get()) }
    factory { NotificationSettingsViewModel(repository = get(), permissions = get()) }

    // 저장한 파일(30-1)
    factory { DownloadsViewModel(downloads = get()) }
}
