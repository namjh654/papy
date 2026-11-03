package cloud.elsa.app.feature.example.detail

import cloud.elsa.app.core.common.error.UiError
import cloud.elsa.app.core.common.viewmodel.UiEvent

sealed interface ExampleDetailEvent : UiEvent {
    /** 수정 실패 — 내용은 그대로 두고 토스트로 알린다 */
    data class SaveFailed(val error: UiError) : ExampleDetailEvent
}
