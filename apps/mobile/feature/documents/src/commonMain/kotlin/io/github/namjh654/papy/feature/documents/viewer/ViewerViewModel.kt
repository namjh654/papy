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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    /** 편집마다 되돌리기 단계 수. 저장했을 때의 깊이와 다르면 저장하지 않은 변경이 있다 */
    private val undoStack = ArrayDeque<Int>()
    private var savedDepth = 0
    /** 엔진 호출을 하나씩 한다(편집·저장이 겹치지 않게) */
    private val engineLock = Mutex()

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

    // --- 편집 ---

    fun toggleEditing() {
        if (!currentState.canEdit) return
        setState { copy(editing = !editing, selection = null) }
    }

    /** 편집 모드에서 쪽을 눌렀다(pt 좌표) */
    fun pageTapped(page: Int, x: Float, y: Float) {
        val id = session ?: return
        if (!currentState.editing) return
        launch {
            val hit = engineLock.withLock { withContext(io) { engine.paragraphAt(id, page, x, y) } }
            setState { copy(selection = hit) }
        }
    }

    fun cancelSelection() = setState { copy(selection = null) }

    /** 고른 문단의 글자를 [text] 로 바꾼다 */
    fun applyEdit(text: String) {
        val id = session ?: return
        val hit = currentState.selection ?: return
        if (text == hit.text) return cancelSelection()
        launch {
            val steps = engineLock.withLock {
                withContext(io) { engine.replaceParagraph(id, hit.section, hit.paragraph, text) }
            }
            if (steps < 0) {
                setState { copy(selection = null) }
                sendEvent(ViewerEvent.EditFailed)
                return@launch
            }
            if (steps > 0) undoStack.addLast(steps)
            setState { copy(selection = null) }
            refreshPages(id)
        }
    }

    fun undo() {
        val id = session ?: return
        val steps = undoStack.removeLastOrNull() ?: return
        launch {
            engineLock.withLock { withContext(io) { repeat(steps) { engine.undo(id) } } }
            if (undoStack.size < savedDepth) savedDepth = -1 // 저장한 것보다 더 되돌렸다 → 저장 상태로 돌아갈 수 없다
            refreshPages(id)
        }
    }

    fun save() {
        val id = session ?: return
        if (currentState.saving) return
        launch {
            setState { copy(saving = true) }
            val error = engineLock.withLock { withContext(io) { engine.save(id, ref) } }
            if (error == null) savedDepth = undoStack.size
            setState { copy(saving = false, dirty = undoStack.size != savedDepth) }
            if (error == null) {
                sendEvent(ViewerEvent.Saved)
                if (currentState.confirmExit) sendEvent(ViewerEvent.Exit)
            } else {
                setState { copy(confirmExit = false) }
                sendEvent(ViewerEvent.SaveFailed(error))
            }
        }
    }

    /** 뒤로 가기: 저장하지 않은 변경이 있으면 묻는다 */
    fun requestExit() = launch {
        if (currentState.dirty) setState { copy(confirmExit = true) } else sendEvent(ViewerEvent.Exit)
    }

    fun discardAndExit() = launch {
        setState { copy(confirmExit = false) }
        sendEvent(ViewerEvent.Exit)
    }

    fun cancelExit() = setState { copy(confirmExit = false) }

    private suspend fun refreshPages(id: Long) {
        val sizes = withContext(io) {
            val count = engine.pageCount(id) ?: currentState.pageCount
            (0 until count).map { engine.pageSize(id, it) ?: FALLBACK_SIZE }
        }
        setState {
            copy(
                document = document?.copy(pageCount = sizes.size),
                pageSizes = sizes,
                currentPage = currentPage.coerceIn(0, (sizes.size - 1).coerceAtLeast(0)),
                revision = revision + 1,
                dirty = undoStack.size != savedDepth,
                canUndo = undoStack.isNotEmpty(),
            )
        }
    }

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
