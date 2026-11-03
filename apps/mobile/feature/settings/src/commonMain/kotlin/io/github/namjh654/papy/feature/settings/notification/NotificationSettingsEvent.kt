package io.github.namjh654.papy.feature.settings.notification

import io.github.namjh654.papy.core.common.error.UiError
import io.github.namjh654.papy.core.common.viewmodel.UiEvent

sealed interface NotificationSettingsEvent : UiEvent {
    /** 광고성 알림 동의·철회 결과 안내(날짜 포함 — 법정 고지). [atMillis]: 처리 시각 */
    data class MarketingChanged(val agreed: Boolean, val atMillis: Long) : NotificationSettingsEvent

    /** 바꾸지 못해 되돌렸다 */
    data class UpdateFailed(val error: UiError) : NotificationSettingsEvent
}
