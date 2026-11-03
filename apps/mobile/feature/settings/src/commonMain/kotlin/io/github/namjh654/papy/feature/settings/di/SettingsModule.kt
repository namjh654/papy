package io.github.namjh654.papy.feature.settings.di

import io.github.namjh654.papy.core.data.config.AppConfig
import io.github.namjh654.papy.core.data.push.devicePlatform
import io.github.namjh654.papy.feature.settings.SettingsViewModel
import io.github.namjh654.papy.feature.settings.downloads.DownloadsViewModel
import io.github.namjh654.papy.feature.settings.inquiry.InquiryApi
import io.github.namjh654.papy.feature.settings.inquiry.InquiryRepository
import io.github.namjh654.papy.feature.settings.inquiry.InquiryViewModel
import io.github.namjh654.papy.feature.settings.inquiry.RemoteInquiryApi
import io.github.namjh654.papy.feature.settings.notification.NotificationSettingsApi
import io.github.namjh654.papy.feature.settings.notification.NotificationSettingsRepository
import io.github.namjh654.papy.feature.settings.notification.NotificationSettingsViewModel
import io.github.namjh654.papy.feature.settings.notification.RemoteNotificationSettingsApi
import io.github.namjh654.papy.feature.settings.profile.ProfileApi
import io.github.namjh654.papy.feature.settings.profile.ProfileRepository
import io.github.namjh654.papy.feature.settings.profile.ProfileViewModel
import io.github.namjh654.papy.feature.settings.profile.RemoteProfileApi
import io.github.namjh654.papy.feature.settings.withdraw.RemoteWithdrawApi
import io.github.namjh654.papy.feature.settings.withdraw.WithdrawApi
import io.github.namjh654.papy.feature.settings.withdraw.WithdrawRepository
import io.github.namjh654.papy.feature.settings.withdraw.WithdrawViewModel
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
