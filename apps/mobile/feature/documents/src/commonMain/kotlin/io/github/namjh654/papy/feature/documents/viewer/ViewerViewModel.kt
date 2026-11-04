package io.github.namjh654.papy.feature.documents.viewer

import io.github.namjh654.papy.core.common.viewmodel.StatefulViewModel
import io.github.namjh654.papy.feature.documents.Caret
import io.github.namjh654.papy.feature.documents.CaretMove
import io.github.namjh654.papy.feature.documents.CellRef
import io.github.namjh654.papy.feature.documents.DocumentEngine
import io.github.namjh654.papy.feature.documents.DocumentError
import io.github.namjh654.papy.feature.documents.EditResult
import io.github.namjh654.papy.feature.documents.ObjectKind
import io.github.namjh654.papy.feature.documents.PageObject
import io.github.namjh654.papy.feature.documents.TableAction
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
/** 새 그림의 너비 상한(쪽 너비에 대한 비율), 그림 크기 단추 한 번의 배율, 그림 크기 범위(pt) */
private const val PICTURE_MAX_PAGE_FRACTION = 0.6f
private const val PICTURE_STEP = 1.25f
private const val MIN_PICTURE = 8f
private const val MAX_PICTURE = 2000f

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
    /** 쪽마다 표·그림 자리(편집하면 비운다) */
    private val objectCache = mutableMapOf<Int, List<PageObject>>()

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
                style = null, extending = false, paragraph = null, selected = null,
            )
        }
    }

    /** 쪽을 눌렀다(pt): 커서를 놓는다. 범위 선택 중이면 선택을 그 자리까지 늘린다 */
    fun tap(page: Int, x: Float, y: Float) = withEngine { id ->
        val objects = objectsOn(id, page)
        // 그림을 누르면 그림을 고른다(커서 없음)
        objects.lastOrNull { it.kind == ObjectKind.Picture && it.contains(x, y) }?.let { picture ->
            setState { copy(caret = null, anchor = null, selectionBoxes = emptyList(), style = null, selected = ObjectSelection(picture)) }
            return@withEngine
        }
        val caret = engine.caretAt(id, page, x, y) ?: return@withEngine
        val state = currentState
        // 칸 안에서는 범위 선택을 아직 하지 않는다
        val anchor = if (state.extending && !caret.position.inCell) state.anchor ?: state.caret?.position?.takeUnless { it.inCell } else null
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

    // --- 표·그림 ---

    /** 커서 자리(본문)에 표를 넣는다 */
    fun insertTable(rows: Int, cols: Int) = insertAtCaret { id, at ->
        engine.insertTable(id, at, rows.coerceIn(1, 100), cols.coerceIn(1, 50))
    }

    /**
     * 커서 자리(본문)에 그림을 넣는다. [source]: 플랫폼 그림 참조. 크기는 픽셀 크기로 정하되 쪽 너비의 60% 를 넘지 않게.
     */
    fun insertPicture(source: String, pixelWidth: Int, pixelHeight: Int) {
        if (pixelWidth <= 0 || pixelHeight <= 0) return trySendEvent(ViewerEvent.PictureFailed)
        insertAtCaret(failure = ViewerEvent.PictureFailed) { id, at ->
            val pageWidth = currentState.pageSizes.getOrNull(currentState.caret?.box?.page ?: 0)?.width ?: 595f
            val width = minOf(pixelWidth * 0.75f, pageWidth * PICTURE_MAX_PAGE_FRACTION).coerceAtLeast(MIN_PICTURE)
            val height = (width * pixelHeight / pixelWidth).coerceIn(MIN_PICTURE, MAX_PICTURE)
            engine.insertPicture(id, at, source, width, height)
        }
    }

    /** 고른 표에 [command] 를 한다(행·열은 커서가 든 칸 기준) */
    fun tableCommand(command: TableCommand) = withEngine { id ->
        val selection = currentState.selected?.takeIf { it.isTable } ?: return@withEngine
        val table = selection.target
        val before = depth
        val cell = selection.cell
        if (command == TableCommand.DeleteTable) {
            val newDepth = engine.deleteObject(id, table.target, ObjectKind.Table)
            if (newDepth < 0) return@withEngine sendEvent(ViewerEvent.EditFailed)
            recordEdit(before, newDepth)
            refreshPages(id)
            return@withEngine setState { copy(caret = null, selected = null) }
        }
        cell ?: return@withEngine
        val (row, col) = cell.row to cell.col
        val lastRow = row + cell.rowSpan - 1
        val lastCol = col + cell.colSpan - 1
        val newDepth = when (command) {
            TableCommand.RowAbove -> engine.editTable(id, table.target, TableAction.InsertRowAbove, row, col, row, col)
            TableCommand.RowBelow -> engine.editTable(id, table.target, TableAction.InsertRowBelow, lastRow, col, lastRow, col)
            TableCommand.ColumnLeft -> engine.editTable(id, table.target, TableAction.InsertColumnLeft, row, col, row, col)
            TableCommand.ColumnRight -> engine.editTable(id, table.target, TableAction.InsertColumnRight, row, lastCol, row, lastCol)
            TableCommand.DeleteRow -> engine.editTable(id, table.target, TableAction.DeleteRow, row, col, row, col)
            TableCommand.DeleteColumn -> engine.editTable(id, table.target, TableAction.DeleteColumn, row, col, row, col)
            TableCommand.MergeRight -> if (lastCol + 1 >= table.cols) return@withEngine
                else engine.editTable(id, table.target, TableAction.Merge, row, col, lastRow, lastCol + 1)
            TableCommand.MergeDown -> if (lastRow + 1 >= table.rows) return@withEngine
                else engine.editTable(id, table.target, TableAction.Merge, row, col, lastRow + 1, lastCol)
            TableCommand.DeleteTable -> return@withEngine
        }
        if (newDepth < 0) return@withEngine sendEvent(ViewerEvent.EditFailed)
        recordEdit(before, newDepth)
        refreshPages(id)
        // 같은 표를 다시 찾고, 기준 칸(지웠으면 가까운 칸)에 커서를 둔다
        val updated = objectsOn(id, table.box.page).firstOrNull { it.target == table.target && it.kind == ObjectKind.Table }
        val nextCell = updated?.cells?.let { cells ->
            cells.firstOrNull { it.row == row && it.col == col }
                ?: cells.minByOrNull { kotlin.math.abs(it.row - row) * 100 + kotlin.math.abs(it.col - col) }
        }
        if (updated == null || nextCell == null) return@withEngine setState { copy(caret = null, selected = null) }
        val caret = engine.caretAtPosition(
            id,
            TextPos(
                table.target.section, table.target.paragraph, 0,
                CellRef(table.target.control, nextCell.cell, 0),
            ),
        )
        setState { copy(caret = caret, anchor = null, selectionBoxes = emptyList(), style = null, selected = ObjectSelection(updated, nextCell)) }
    }

    /** 고른 그림을 [steps] 만큼 키우거나(+) 줄인다(-) */
    fun resizePicture(steps: Int) = withEngine { id ->
        val picture = currentState.selected?.takeIf { it.isPicture }?.target ?: return@withEngine
        var factor = 1f
        repeat(kotlin.math.abs(steps)) { factor *= if (steps > 0) PICTURE_STEP else 1f / PICTURE_STEP }
        val width = (picture.box.width * factor).coerceIn(MIN_PICTURE, MAX_PICTURE)
        val height = (picture.box.height * width / picture.box.width.coerceAtLeast(1f)).coerceIn(MIN_PICTURE, MAX_PICTURE)
        val before = depth
        val newDepth = engine.resizePicture(id, picture.target, width, height)
        if (newDepth < 0) return@withEngine sendEvent(ViewerEvent.EditFailed)
        recordEdit(before, newDepth)
        refreshPages(id)
        val updated = (0 until currentState.pageCount).asSequence()
            .flatMap { objectsOn(id, it).asSequence() }
            .firstOrNull { it.target == picture.target && it.kind == ObjectKind.Picture }
        setState { copy(selected = updated?.let { ObjectSelection(it) }) }
    }

    /** 고른 그림을 지운다 */
    fun deletePicture() = withEngine { id ->
        val picture = currentState.selected?.takeIf { it.isPicture }?.target ?: return@withEngine
        val before = depth
        val newDepth = engine.deleteObject(id, picture.target, ObjectKind.Picture)
        if (newDepth < 0) return@withEngine sendEvent(ViewerEvent.EditFailed)
        recordEdit(before, newDepth)
        refreshPages(id)
        setState { copy(selected = null) }
    }

    fun clearObject() = setState { copy(selected = null) }

    /** 커서 자리(본문)에 개체를 넣는 공통 동작 */
    private fun insertAtCaret(failure: ViewerEvent = ViewerEvent.EditFailed, block: suspend (Long, TextPos) -> Int) =
        withEngine { id ->
            val at = currentState.caret?.position ?: return@withEngine
            if (at.inCell) return@withEngine sendEvent(ViewerEvent.InsertNeedsBody)
            val before = depth
            val newDepth = block(id, at)
            if (newDepth < 0) return@withEngine sendEvent(failure)
            recordEdit(before, newDepth)
            refreshPages(id)
            setState { copy(caret = null, anchor = null, selectionBoxes = emptyList(), selected = null) }
        }

    private fun objectsOn(id: Long, page: Int): List<PageObject> =
        objectCache.getOrPut(page) { engine.pageObjects(id, page) }

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
            else setState { copy(caret = null, anchor = null, selectionBoxes = emptyList(), selected = null) }
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
        // 칸 안 커서: 그 표를 고른다(표 도구 막대)
        val cellRef = caret.position.cell
        val selected = cellRef?.let { ref ->
            val table = objectsOn(id, caret.box.page).firstOrNull {
                it.kind == ObjectKind.Table && it.target.paragraph == caret.position.paragraph && it.target.control == ref.control
            }
            table?.let { ObjectSelection(it, it.cells.firstOrNull { cell -> cell.cell == ref.cell }) }
        }
        val range = anchor?.let { TextRange.of(it, caret.position) }?.takeUnless { it.isEmpty || caret.position.inCell }
        val boxes = range?.let { engine.selectionBoxes(id, it) }.orEmpty()
        // 선택이 있으면 선택 첫 글자의 모양(커서 앞 글자를 읽으므로 한 글자 뒤 위치)
        val styleAt = range?.start?.let { it.copy(offset = it.offset + 1) } ?: caret.position
        val style = engine.textStyle(id, styleAt)
        setState {
            copy(caret = caret, anchor = range?.let { anchor }, selectionBoxes = boxes, style = style, selected = selected)
        }
    }

    private fun refreshPages(id: Long) {
        objectCache.clear()
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
