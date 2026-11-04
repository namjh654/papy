package io.github.namjh654.papy.feature.documents

import io.github.namjh654.papy.core.common.viewmodel.StatefulViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** 첫 화면: 최근 문서 목록과 "문서 열기"(파일 고르기는 플랫폼이 하고 [fileChosen] 으로 알린다) */
class DocumentsViewModel(
    private val recents: RecentDocuments,
) : StatefulViewModel<DocumentsUiState, DocumentsEvent>(DocumentsUiState()) {

    val uiState: StateFlow<DocumentsUiState> = state

    val events: Flow<DocumentsEvent> = eventFlow

    init {
        launch {
            recents.load()
            recents.items.collect { list -> setState { copy(recent = list, loading = false) } }
        }
    }

    fun fileChosen(ref: String, name: String) = launch { sendEvent(DocumentsEvent.Open(ref, name)) }

    fun recentClicked(item: RecentDocument) = launch { sendEvent(DocumentsEvent.Open(item.ref, item.name)) }

    fun removeRecent(item: RecentDocument) = launch { recents.remove(item.ref) }

    fun clearRecent() = launch { recents.clear() }
}
