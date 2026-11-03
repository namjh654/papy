package io.github.namjh654.papy.feature.settings

import io.github.namjh654.papy.core.common.viewmodel.UiEvent
import io.github.namjh654.papy.core.data.settings.AppLanguage

sealed interface SettingsEvent : UiEvent {
    data object LoggedOut : SettingsEvent

    /** 언어를 바꿨다 → Android 는 Activity 재생성, iOS 는 다음 실행부터 적용 안내 */
    data class LanguageChanged(val language: AppLanguage) : SettingsEvent
}
