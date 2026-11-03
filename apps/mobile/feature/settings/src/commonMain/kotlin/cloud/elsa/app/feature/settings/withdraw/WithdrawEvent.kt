package cloud.elsa.app.feature.settings.withdraw

import cloud.elsa.app.core.common.viewmodel.UiEvent

sealed interface WithdrawEvent : UiEvent {
    /** 탈퇴 완료 → 로그인 화면으로 */
    data object Withdrawn : WithdrawEvent
}
