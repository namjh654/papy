package io.github.namjh654.papy.feature.settings.withdraw

import io.github.namjh654.papy.core.common.viewmodel.UiEvent

sealed interface WithdrawEvent : UiEvent {
    /** 탈퇴 완료 → 로그인 화면으로 */
    data object Withdrawn : WithdrawEvent
}
