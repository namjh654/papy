package io.github.namjh654.papy.feature.documents.viewer

import io.github.namjh654.papy.core.common.viewmodel.UiEvent
import io.github.namjh654.papy.core.common.viewmodel.UiState
import io.github.namjh654.papy.feature.documents.DocumentError
import io.github.namjh654.papy.feature.documents.OpenedDocument
import io.github.namjh654.papy.feature.documents.PageSizePt

data class ViewerUiState(
    val name: String,
    val phase: ViewerPhase = ViewerPhase.Opening,
    val document: OpenedDocument? = null,
    /** 쪽마다 크기(pt). 쪽 수와 같은 길이 */
    val pageSizes: List<PageSizePt> = emptyList(),
    /** 지금 보이는 쪽(0부터) */
    val currentPage: Int = 0,
    /** 암호를 넣었는데 틀렸다 */
    val wrongPassword: Boolean = false,
    val error: DocumentError? = null,
    /** 글자 보기 창: null 이면 닫힘 */
    val text: PageText? = null,
    /** 옮기지 못한 요소 안내를 닫았다 */
    val lossDismissed: Boolean = false,
) : UiState {
    val pageCount: Int get() = document?.pageCount ?: 0
    val showLoss: Boolean get() = (document?.droppedObjects ?: 0) > 0 && !lossDismissed
}

/** 글자 보기 창 내용. [text] 가 null 이면 읽는 중, [unavailable] 이면 이 쪽 글자를 읽을 수 없음 */
data class PageText(val page: Int, val text: String? = null, val unavailable: Boolean = false)

enum class ViewerPhase { Opening, NeedsPassword, Ready, Failed }

sealed interface ViewerEvent : UiEvent
