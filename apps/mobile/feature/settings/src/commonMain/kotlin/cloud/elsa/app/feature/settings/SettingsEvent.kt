package cloud.elsa.app.feature.settings

import cloud.elsa.app.core.common.viewmodel.UiEvent
import cloud.elsa.app.core.data.settings.AppLanguage

sealed interface SettingsEvent : UiEvent {
    data object LoggedOut : SettingsEvent

    /** 언어를 바꿨다 → Android 는 Activity 재생성, iOS 는 다음 실행부터 적용 안내 */
    data class LanguageChanged(val language: AppLanguage) : SettingsEvent
}
