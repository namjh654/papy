package io.github.namjh654.papy.feature.documents.viewer

import io.github.namjh654.papy.core.common.viewmodel.StatefulViewModel
import io.github.namjh654.papy.feature.documents.DocumentEngine
import io.github.namjh654.papy.feature.documents.DocumentError
import io.github.namjh654.papy.feature.documents.PageSizePt
import io.github.namjh654.papy.feature.documents.RecentDocuments
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/** A4 세로(pt) — 쪽 크기를 못 읽었을 때 */
private val FALLBACK_SIZE = PageSizePt(595f, 842f)

/**
 * 문서 보기: 열기 → (암호) → 쪽 크기 읽기. 화면이 사라지면 문서를 닫는다.
 * 엔진 호출은 막는 호출이라 [io] 에서 한다.
 */
class ViewerViewModel(
    private val ref: String,
    name: String,
    private val engine: DocumentEngine,
    private val recents: RecentDocuments,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : StatefulViewModel<ViewerUiState, ViewerEvent>(ViewerUiState(name = name)) {

    val uiState: StateFlow<ViewerUiState> = state

    val events: Flow<ViewerEvent> = eventFlow

    private var session: Long? = null

    init {
        open(password = null)
    }

    fun submitPassword(password: String) {
        if (password.isEmpty()) return
        open(password)
    }

    /** 다시 시도(오류 화면) */
    fun retry() = open(password = null)

    fun pageVisible(index: Int) {
        if (index in 0 until currentState.pageCount && index != currentState.currentPage) {
            setState { copy(currentPage = index) }
        }
    }

    fun dismissLoss() = setState { copy(lossDismissed = true) }

    /** 지금 쪽의 글자를 읽어 창으로 보여 준다 */
    fun showText() {
        val id = session ?: return
        val page = currentState.currentPage
        setState { copy(text = PageText(page)) }
        launch {
            val text = withContext(io) { engine.pageText(id, page) }
            setState {
                if (this.text?.page != page) this
                else copy(text = PageText(page, text = text.orEmpty(), unavailable = text.isNullOrBlank()))
            }
        }
    }

    fun hideText() = setState { copy(text = null) }

    private fun open(password: String?) = launch {
        setState { copy(phase = ViewerPhase.Opening, error = null) }
        val result = withContext(io) { engine.open(ref, password) }
        val document = result.document
        if (document == null) {
            val error = result.error ?: DocumentError.Internal
            setState {
                if (error == DocumentError.PasswordRequired) {
                    copy(phase = ViewerPhase.NeedsPassword, wrongPassword = password != null)
                } else {
                    copy(phase = ViewerPhase.Failed, error = error)
                }
            }
            // 열 수 없는 파일은 최근 목록에서 뺀다(지워졌거나 권한이 사라짐).
            if (error == DocumentError.Unreadable) recents.remove(ref)
            return@launch
        }
        session = document.session
        val sizes = withContext(io) {
            (0 until document.pageCount).map { engine.pageSize(document.session, it) ?: FALLBACK_SIZE }
        }
        setState {
            copy(phase = ViewerPhase.Ready, document = document, pageSizes = sizes, wrongPassword = false, currentPage = 0)
        }
        recents.add(ref, currentState.name, document.kind)
    }

    override fun onCleared() {
        session?.let(engine::close)
        session = null
        super.onCleared()
    }
}
