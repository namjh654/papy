package io.github.namjh654.papy.feature.documents.viewer

import io.github.namjh654.papy.core.common.viewmodel.StatefulViewModel
import io.github.namjh654.papy.feature.documents.Caret
import io.github.namjh654.papy.feature.documents.CaretMove
import io.github.namjh654.papy.feature.documents.DocumentEngine
import io.github.namjh654.papy.feature.documents.DocumentError
import io.github.namjh654.papy.feature.documents.EditResult
import io.github.namjh654.papy.feature.documents.PageSizePt
import io.github.namjh654.papy.feature.documents.RecentDocuments
import io.github.namjh654.papy.feature.documents.TextPos
import io.github.namjh654.papy.feature.documents.TextRange
import io.github.namjh654.papy.feature.documents.TextStyle
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

/** 글자 크기 단추 한 번에 바꾸는 크기(pt), 범위 */
private const val FONT_STEP = 1f
private const val MIN_FONT = 4f
private const val MAX_FONT = 200f

/**
 * 문서 보기·편집: 열기 → (암호) → 쪽 크기 읽기. 편집 모드에서는 커서를 놓고 글자를 치거나 지우고,
 * 고른 범위에 글자 모양을 바꾼다. 화면이 사라지면 문서를 닫는다.
 *
 * 엔진 호출은 막는 호출이라 [io] 에서 하고, [engineLock] 으로 하나씩 한다 — 빠르게 친 글자가 차례대로
 * 들어가도록 커서 위치도 잠근 안에서 읽는다.
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
    /** 엔진의 되돌리기 단계 수 */
    private var depth = 0
    /** 사용자 동작마다 그 전의 단계 수. 되돌리기는 한 동작(여러 단계일 수 있다)을 통째로 되돌린다 */
    private val groups = ArrayDeque<Int>()
    /** 저장한 뒤 바뀐 것이 있다(되돌려서 저장한 상태로 돌아가도 바뀐 것으로 본다 — 안전한 쪽) */
    private var modified = false
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

    // --- 편집 모드 ---

    fun toggleEditing() {
        if (!currentState.canEdit) return
        setState {
            copy(
                editing = !editing, caret = null, anchor = null, selectionBoxes = emptyList(),
                style = null, extending = false, paragraph = null,
            )
        }
    }

    /** 쪽을 눌렀다(pt): 커서를 놓는다. 범위 선택 중이면 선택을 그 자리까지 늘린다 */
    fun tap(page: Int, x: Float, y: Float) = withEngine { id ->
        val caret = engine.caretAt(id, page, x, y) ?: return@withEngine
        val state = currentState
        val anchor = if (state.extending) state.anchor ?: state.caret?.position else null
        showCaret(id, caret, anchor)
    }

    /** 두 번 눌렀다: 단어를 고른다 */
    fun doubleTap(page: Int, x: Float, y: Float) = withEngine { id ->
        val caret = engine.caretAt(id, page, x, y) ?: return@withEngine
        val word = engine.wordAt(id, caret.position)
        if (word == null || word.isEmpty) return@withEngine showCaret(id, caret, null)
        val end = engine.caretAtPosition(id, word.end) ?: return@withEngine
        showCaret(id, end, word.start)
    }

    /** 길게 눌렀다: 문단 고치기 창을 연다 */
    fun longPress(page: Int, x: Float, y: Float) = withEngine { id ->
        val hit = engine.paragraphAt(id, page, x, y)
        setState { copy(paragraph = hit) }
    }

    fun toggleExtending() = setState { copy(extending = !extending) }

    /** 선택을 풀고 커서만 남긴다 */
    fun collapseSelection() = setState { copy(anchor = null, selectionBoxes = emptyList(), extending = false) }

    fun moveCaret(move: CaretMove) = withEngine { id ->
        val state = currentState
        val caret = state.caret ?: return@withEngine
        val selection = state.selection
        // 선택이 있으면 왼쪽·오른쪽은 선택의 앞·뒤 끝으로 접는다.
        val target = when {
            state.extending -> engine.moveCaret(id, caret.position, move, caret.box.x)
            selection != null && move == CaretMove.Left -> engine.caretAtPosition(id, selection.start)
            selection != null && move == CaretMove.Right -> engine.caretAtPosition(id, selection.end)
            else -> engine.moveCaret(id, caret.position, move, caret.box.x)
        } ?: return@withEngine
        val anchor = if (state.extending) state.anchor ?: caret.position else null
        showCaret(id, target, anchor)
    }

    /** 커서 자리에 글자를 넣는다. 선택이 있으면 선택을 바꾼다 */
    fun type(text: String) {
        if (text.isEmpty()) return
        edit { id ->
            val at = removeSelection(id) ?: currentState.caret?.position ?: return@edit null
            engine.insertText(id, at, text)
        }
    }

    /** 커서 앞 글자(또는 선택)를 지운다 */
    fun backspace() = edit { id ->
        currentState.selection?.let { return@edit engine.deleteRange(id, it) }
        val caret = currentState.caret ?: return@edit null
        engine.deleteBackward(id, caret.position)
    }

    /** 굵게·기울임·밑줄·취소선을 켜거나 끈다. 선택이 없으면 커서가 든 단어에 적용한다 */
    fun toggleStyle(toggle: StyleToggle) {
        val current = currentState.style
        val style = when (toggle) {
            StyleToggle.Bold -> TextStyle(bold = current?.bold != true)
            StyleToggle.Italic -> TextStyle(italic = current?.italic != true)
            StyleToggle.Underline -> TextStyle(underline = current?.underline != true)
            StyleToggle.Strikethrough -> TextStyle(strikethrough = current?.strikethrough != true)
        }
        applyStyle(style)
    }

    /** 글자 크기를 [steps] 만큼 키우거나(+) 줄인다(-) */
    fun changeFontSize(steps: Int) {
        val size = currentState.style?.fontSize ?: return
        val next = (size + steps * FONT_STEP).coerceIn(MIN_FONT, MAX_FONT)
        if (next != size) applyStyle(TextStyle(fontSize = next))
    }

    /** 글자 색(`#rrggbb`) */
    fun setColor(color: String) = applyStyle(TextStyle(color = color))

    private fun applyStyle(style: TextStyle) = withEngine { id ->
        val state = currentState
        val caret = state.caret ?: return@withEngine
        val range = state.selection ?: engine.wordAt(id, caret.position)?.takeUnless { it.isEmpty }
            ?: return@withEngine
        val before = depth
        val newDepth = engine.applyTextStyle(id, range, style)
        if (newDepth < 0) return@withEngine sendEvent(ViewerEvent.EditFailed)
        recordEdit(before, newDepth)
        refreshPages(id)
        // 선택을 그대로 보여 준다(새 모양으로 다시 잰다)
        val end = engine.caretAtPosition(id, caret.position) ?: caret
        showCaret(id, end, state.anchor.takeIf { state.selection != null })
    }

    fun cancelParagraph() = setState { copy(paragraph = null) }

    /** 문단 고치기 창의 글자로 문단을 바꾼다 */
    fun applyParagraph(text: String) {
        val hit = currentState.paragraph ?: return
        if (text == hit.text) return cancelParagraph()
        withEngine { id ->
            val before = depth
            val newDepth = engine.replaceParagraph(id, hit.section, hit.paragraph, text)
            setState { copy(paragraph = null, caret = null, anchor = null, selectionBoxes = emptyList()) }
            if (newDepth < 0) return@withEngine sendEvent(ViewerEvent.EditFailed)
            recordEdit(before, newDepth)
            refreshPages(id)
        }
    }

    /** 마지막 동작을 되돌린다 */
    fun undo() {
        val target = groups.removeLastOrNull() ?: return
        withEngine(requireEditing = false) { id ->
            var caret: Caret? = null
            while (depth > target) {
                val result = engine.undo(id) ?: break
                if (result.undoDepth >= depth) break // 되돌린 것이 없다
                depth = result.undoDepth
                caret = result.caret ?: caret
            }
            modified = true
            refreshPages(id)
            if (caret != null && currentState.editing) showCaret(id, caret, null)
            else setState { copy(caret = null, anchor = null, selectionBoxes = emptyList()) }
        }
    }

    fun save() {
        val id = session ?: return
        if (currentState.saving) return
        launch {
            setState { copy(saving = true) }
            val error = engineLock.withLock { withContext(io) { engine.save(id, ref) } }
            if (error == null) modified = false
            setState { copy(saving = false, dirty = modified) }
            if (error == null) {
                sendEvent(ViewerEvent.Saved)
                if (currentState.confirmExit) sendEvent(ViewerEvent.Exit)
            } else {
                setState { copy(confirmExit = false) }
                sendEvent(ViewerEvent.SaveFailed(error))
            }
        }
    }

    /** PDF 로 바꿔 [target] 에 쓴다. 작업 문서는 그대로다 */
    fun exportPdf(target: String) {
        val id = session ?: return
        if (currentState.exporting || !currentState.canExportPdf) return
        launch {
            setState { copy(exporting = true) }
            val error = engineLock.withLock { withContext(io) { engine.exportPdf(id, target) } }
            setState { copy(exporting = false) }
            sendEvent(if (error == null) ViewerEvent.Exported(target) else ViewerEvent.ExportFailed(error))
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

    // --- 내부 ---

    /** (편집 모드에서) 엔진을 잠그고 [io] 에서 [block] 을 부른다 */
    private fun withEngine(requireEditing: Boolean = true, block: suspend (Long) -> Unit) {
        val id = session ?: return
        if (requireEditing && !currentState.editing) return
        launch { engineLock.withLock { withContext(io) { block(id) } } }
    }

    /** 글자를 바꾸는 동작: 결과 커서를 보여 주고 쪽을 다시 그린다 */
    private fun edit(block: suspend (Long) -> EditResult?) =
        withEngine { id ->
            val before = depth
            val result = block(id)
            if (result == null) {
                // 선택을 지운 뒤 넣기가 실패했을 수도 있다 — 단계 수를 맞춘다.
                if (depth != before) recordEdit(before, depth)
                return@withEngine sendEvent(ViewerEvent.EditFailed)
            }
            recordEdit(before, result.undoDepth)
            refreshPages(id)
            showCaret(id, result.caret, null)
        }

    /** 선택이 있으면 지우고 그 자리를 돌려준다 */
    private fun removeSelection(id: Long): TextPos? {
        val selection = currentState.selection ?: return null
        val result = engine.deleteRange(id, selection) ?: return null
        depth = result.undoDepth
        return result.caret.position
    }

    private fun recordEdit(before: Int, after: Int) {
        if (after != before) groups.addLast(before)
        depth = after
        modified = true
    }

    /** 커서·선택을 보여 주고 그 자리의 글자 모양을 읽는다 */
    private fun showCaret(id: Long, caret: Caret, anchor: TextPos?) {
        val range = anchor?.let { TextRange.of(it, caret.position) }?.takeUnless { it.isEmpty }
        val boxes = range?.let { engine.selectionBoxes(id, it) }.orEmpty()
        // 선택이 있으면 선택 첫 글자의 모양(커서 앞 글자를 읽으므로 한 글자 뒤 위치)
        val styleAt = range?.start?.let { it.copy(offset = it.offset + 1) } ?: caret.position
        val style = engine.textStyle(id, styleAt)
        setState {
            copy(caret = caret, anchor = range?.let { anchor }, selectionBoxes = boxes, style = style)
        }
    }

    private fun refreshPages(id: Long) {
        val count = engine.pageCount(id) ?: currentState.pageCount
        val sizes = (0 until count).map { engine.pageSize(id, it) ?: FALLBACK_SIZE }
        setState {
            copy(
                document = document?.copy(pageCount = sizes.size),
                pageSizes = sizes,
                currentPage = currentPage.coerceIn(0, (sizes.size - 1).coerceAtLeast(0)),
                revision = revision + 1,
                dirty = modified,
                canUndo = groups.isNotEmpty(),
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
