package cloud.elsa.app.feature.settings

import cloud.elsa.app.core.common.viewmodel.StatefulViewModel
import cloud.elsa.app.core.data.config.AppConfig
import cloud.elsa.app.core.data.monitoring.AnalyticsTracker
import cloud.elsa.app.core.data.review.InAppReview
import cloud.elsa.app.core.data.security.AppLock
import cloud.elsa.app.core.data.session.SessionRepository
import cloud.elsa.app.core.data.session.UserStore
import cloud.elsa.app.core.data.settings.AppLanguage
import cloud.elsa.app.core.data.settings.AppSettingsRepository
import cloud.elsa.app.core.data.settings.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 설정 탭. 테마는 저장하면 루트가 바로 적용하고(AppSettingsRepository.theme 관찰), 언어는 이벤트로 플랫폼에 알린다.
 * 새 설정 항목: 값은 [AppSettingsRepository] 에, 화면 행은 플랫폼 ElsaSettingsRow 로 추가한다.
 */
class SettingsViewModel(
    private val appSettings: AppSettingsRepository,
    private val sessionRepository: SessionRepository,
    private val userStore: UserStore,
    private val inAppReview: InAppReview,
    private val analytics: AnalyticsTracker,
    private val appLock: AppLock,
) : StatefulViewModel<SettingsUiState, SettingsEvent>(
    SettingsUiState(
        user = userStore.user.value,
        theme = appSettings.theme.value,
        language = appSettings.language.value,
        environment = AppConfig.ENVIRONMENT.takeIf { AppConfig.APP_SHOW_ENVIRONMENT },
        termsUrl = AppConfig.URL_TERMS.ifBlank { null },
        privacyUrl = AppConfig.URL_PRIVACY.ifBlank { null },
    ),
) {

    val uiState: StateFlow<SettingsUiState> = state

    val events: Flow<SettingsEvent> = eventFlow

    init {
        launch { appSettings.theme.collect { theme -> setState { copy(theme = theme) } } }
        launch { appSettings.language.collect { language -> setState { copy(language = language) } } }
        // 프로필을 고치면 바로 따라온다
        launch { userStore.user.collect { user -> setState { copy(user = user) } } }
        launch { appLock.isEnabled.collect { enabled -> setState { copy(appLockEnabled = enabled) } } }
    }

    /**
     * 앱 잠금 켜기·끄기(26-1). 켤 때는 화면이 먼저 생체 인증·기기 암호로 본인 확인을 하고 성공하면 부른다.
     * 기기에 잠금 수단이 없으면 화면이 행을 숨긴다.
     */
    fun appLockChanged(enabled: Boolean) = launch {
        appLock.setEnabled(enabled)
    }

    fun selectTheme(mode: ThemeMode) = launch {
        appSettings.setTheme(mode)
        // 이벤트 기록 예시: 이름·키는 snake_case, 값은 문자열
        analytics.logEvent("theme_changed", mapOf("mode" to mode.name.lowercase()))
    }

    /** 앱 평가하기: OS 가 보여줄지 정한다(안 뜰 수 있음) */
    fun rateApp() {
        inAppReview.requestReview()
    }

    fun selectLanguage(language: AppLanguage) = launch {
        if (language == currentState.language) return@launch
        appSettings.setLanguage(language)
        sendEvent(SettingsEvent.LanguageChanged(language))
    }

    fun logout() = launch {
        if (currentState.isLoggingOut) return@launch

        setState { copy(isLoggingOut = true) }
        try {
            sessionRepository.logout()
            sendEvent(SettingsEvent.LoggedOut)
        } finally {
            setState { copy(isLoggingOut = false) }
        }
    }
}
