package io.github.namjh654.papy.feature.example.detail

import io.github.namjh654.papy.core.common.error.UiError
import io.github.namjh654.papy.core.common.viewmodel.UiEvent

sealed interface ExampleDetailEvent : UiEvent {
    /** 수정 실패 — 내용은 그대로 두고 토스트로 알린다 */
    data class SaveFailed(val error: UiError) : ExampleDetailEvent
}
