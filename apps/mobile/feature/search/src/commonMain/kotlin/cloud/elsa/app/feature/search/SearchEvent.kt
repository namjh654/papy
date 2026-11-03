package cloud.elsa.app.feature.search

import cloud.elsa.app.core.common.viewmodel.UiEvent

sealed interface SearchEvent : UiEvent {
    /** 결과를 눌렀다 → 상세 화면으로(id) */
    data class ItemSelected(val id: String) : SearchEvent
}
