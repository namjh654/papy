package io.github.namjh654.papy.feature.documents

import io.github.namjh654.papy.core.common.viewmodel.UiEvent
import io.github.namjh654.papy.core.common.viewmodel.UiState

data class DocumentsUiState(
    val recent: List<RecentDocument> = emptyList(),
    val loading: Boolean = true,
) : UiState

sealed interface DocumentsEvent : UiEvent {
    /** 문서 보기로 간다 */
    data class Open(val ref: String, val name: String) : DocumentsEvent
}
