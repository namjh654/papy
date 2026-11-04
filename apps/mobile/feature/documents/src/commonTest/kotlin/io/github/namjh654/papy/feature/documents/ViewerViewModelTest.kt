package io.github.namjh654.papy.feature.documents

import io.github.namjh654.papy.core.data.storage.LocalStorage
import app.cash.turbine.test
import io.github.namjh654.papy.feature.documents.viewer.PageText
import io.github.namjh654.papy.feature.documents.viewer.StyleToggle
import io.github.namjh654.papy.feature.documents.viewer.ViewerEvent
import io.github.namjh654.papy.feature.documents.viewer.ViewerPhase
import io.github.namjh654.papy.feature.documents.viewer.ViewerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MemoryStorage : LocalStorage {
    val values = mutableMapOf<String, String>()
    override suspend fun getString(key: String) = values[key]
    override suspend fun setString(key: String, value: String) { values[key] = value }
    override suspend fun remove(key: String) { values.remove(key) }
    override suspend fun clear() = values.clear()
}

class FakeEngine(
    private val password: String? = null,
    private val error: DocumentError? = null,
    private val saveError: DocumentError? = null,
) : DocumentEngine {
    val closed = mutableListOf<Long>()
    var opens = 0
    var pages = 3
    val paragraphs = mutableListOf("첫 문단", "둘째 문단")
    val saved = mutableListOf<String>()
    val exported = mutableListOf<String>()
    var exportError: DocumentError? = null
    /** 되돌리기 단계(엔진처럼 이어 친 글자는 한 단계) */
    var depth = 0
    private var typingAt: TextPos? = null
    val styles = mutableListOf<Pair<TextRange, TextStyle>>()
    var bold = false
    var size = 10f

    private fun box(pos: TextPos) = PageBox(0, pos.offset * 10f, pos.paragraph * 100f, 0f, 20f)
    private fun caret(pos: TextPos) = Caret(pos, box(pos))
    private fun step() { depth++; typingAt = null }

    override fun open(ref: String, password: String?): OpenResult {
        opens++
        if (error != null) return OpenResult.failure(error)
        if (this.password != null && password != this.password) return OpenResult.failure(DocumentError.PasswordRequired)
        return OpenResult.success(OpenedDocument(session = 7, kind = DocumentKind.Hwp, pageCount = pages, title = null, droppedObjects = 2, canEdit = true))
    }
    override fun pageSize(session: Long, page: Int) = if (page == 1) null else PageSizePt(100f + page, 200f)
    override fun pageText(session: Long, page: Int) = if (page == 0) "첫 쪽" else null
    override fun pageCount(session: Long) = pages
    override fun paragraphAt(session: Long, page: Int, x: Float, y: Float): ParagraphHit? {
        val index = y.toInt() / 100
        val text = paragraphs.getOrNull(index) ?: return null
        return ParagraphHit(0, index, text, listOf(PageBox(page, 0f, index * 100f, 100f, 20f)))
    }
    override fun replaceParagraph(session: Long, section: Int, paragraph: Int, text: String): Int {
        if (paragraph !in paragraphs.indices) return -1
        paragraphs[paragraph] = text
        pages = 4 // 글이 길어져 쪽이 늘었다
        step(); step()
        return depth
    }
    override fun caretAt(session: Long, page: Int, x: Float, y: Float): Caret? {
        val paragraph = (y.toInt() / 100).coerceIn(0, paragraphs.lastIndex)
        val offset = (x.toInt() / 10).coerceIn(0, paragraphs[paragraph].length)
        return caret(TextPos(0, paragraph, offset))
    }
    override fun caretAtPosition(session: Long, position: TextPos) = caret(position)
    override fun moveCaret(session: Long, position: TextPos, move: CaretMove, preferredX: Float): Caret {
        val text = paragraphs[position.paragraph]
        return caret(
            when (move) {
                CaretMove.Left -> position.copy(offset = (position.offset - 1).coerceAtLeast(0))
                CaretMove.Right -> position.copy(offset = (position.offset + 1).coerceAtMost(text.length))
                CaretMove.LineStart -> position.copy(offset = 0)
                CaretMove.LineEnd -> position.copy(offset = text.length)
                CaretMove.Up -> position.copy(paragraph = (position.paragraph - 1).coerceAtLeast(0), offset = 0)
                CaretMove.Down -> position.copy(paragraph = (position.paragraph + 1).coerceAtMost(paragraphs.lastIndex), offset = 0)
            },
        )
    }
    override fun insertText(session: Long, position: TextPos, text: String): EditResult {
        val p = paragraphs[position.paragraph]
        paragraphs[position.paragraph] = p.substring(0, position.offset) + text + p.substring(position.offset)
        if (typingAt != position) step()
        val after = position.copy(offset = position.offset + text.length)
        typingAt = after
        return EditResult(caret(after), depth)
    }
    override fun deleteBackward(session: Long, position: TextPos): EditResult {
        if (position.offset == 0) return EditResult(caret(position), depth)
        val p = paragraphs[position.paragraph]
        paragraphs[position.paragraph] = p.removeRange(position.offset - 1, position.offset)
        step()
        return EditResult(caret(position.copy(offset = position.offset - 1)), depth)
    }
    override fun deleteRange(session: Long, range: TextRange): EditResult {
        val p = paragraphs[range.start.paragraph]
        paragraphs[range.start.paragraph] = p.removeRange(range.start.offset, range.end.offset)
        step()
        return EditResult(caret(range.start), depth)
    }
    override fun selectionBoxes(session: Long, range: TextRange) = listOf(box(range.start))
    override fun wordAt(session: Long, position: TextPos): TextRange? {
        val text = paragraphs[position.paragraph]
        var start = position.offset
        while (start > 0 && text[start - 1] != ' ') start--
        var end = position.offset
        while (end < text.length && text[end] != ' ') end++
        return TextRange(position.copy(offset = start), position.copy(offset = end))
    }
    override fun textStyle(session: Long, position: TextPos) = TextStyle(fontSize = size, bold = bold, italic = false)
    override fun applyTextStyle(session: Long, range: TextRange, style: TextStyle): Int {
        styles += range to style
        style.bold?.let { bold = it }
        style.fontSize?.let { size = it }
        step()
        return depth
    }
    override fun undo(session: Long): UndoResult? {
        if (depth == 0) return UndoResult(null, 0)
        depth--
        typingAt = null
        return UndoResult(caret(TextPos(0, 0, 0)), depth)
    }
    override fun save(session: Long, ref: String): DocumentError? { if (saveError == null) saved += ref; return saveError }
    override fun exportPdf(session: Long, target: String): DocumentError? {
        if (exportError == null) exported += target
        return exportError
    }
    override fun close(session: Long) { closed += session }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ViewerViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun recents() = RecentDocuments(MemoryStorage(), now = { 1L })

    @Test
    fun opensReadsSizesAndRemembers() = runTest(dispatcher) {
        val engine = FakeEngine()
        val recents = recents()
        val vm = ViewerViewModel("ref", "a.hwp", engine, recents, io = dispatcher)
        val state = vm.uiState.value
        assertEquals(ViewerPhase.Ready, state.phase)
        assertEquals(3, state.pageCount)
        // 읽지 못한 쪽은 A4 로
        assertEquals(listOf(PageSizePt(100f, 200f), PageSizePt(595f, 842f), PageSizePt(102f, 200f)), state.pageSizes)
        assertTrue(state.showLoss)
        vm.dismissLoss()
        assertFalse(vm.uiState.value.showLoss)
        vm.pageVisible(2)
        assertEquals(2, vm.uiState.value.currentPage)
        vm.pageVisible(9)
        assertEquals(2, vm.uiState.value.currentPage)
        assertEquals(listOf("ref"), recents.items.value.map { it.ref })
        // 글자 보기: 글자가 없는 쪽은 읽을 수 없다고 알린다
        vm.showText()
        assertEquals(PageText(2, text = "", unavailable = true), vm.uiState.value.text)
        vm.pageVisible(0)
        vm.showText()
        assertEquals(PageText(0, text = "첫 쪽"), vm.uiState.value.text)
        vm.hideText()
        assertNull(vm.uiState.value.text)
        assertEquals(DocumentKind.Hwp, recents.items.value.single().kind)
    }

    @Test
    fun asksForPasswordAndReportsWrongOne() = runTest(dispatcher) {
        val vm = ViewerViewModel("ref", "secret.hwpx", FakeEngine(password = "pw"), recents(), io = dispatcher)
        assertEquals(ViewerPhase.NeedsPassword, vm.uiState.value.phase)
        assertFalse(vm.uiState.value.wrongPassword)
        vm.submitPassword("nope")
        assertEquals(ViewerPhase.NeedsPassword, vm.uiState.value.phase)
        assertTrue(vm.uiState.value.wrongPassword)
        vm.submitPassword("pw")
        assertEquals(ViewerPhase.Ready, vm.uiState.value.phase)
        assertFalse(vm.uiState.value.wrongPassword)
    }

    @Test
    fun unreadableFileFailsAndLeavesRecents() = runTest(dispatcher) {
        val recents = recents()
        recents.add("ref", "gone.docx", DocumentKind.Docx)
        val engine = FakeEngine(error = DocumentError.Unreadable)
        val vm = ViewerViewModel("ref", "gone.docx", engine, recents, io = dispatcher)
        assertEquals(ViewerPhase.Failed, vm.uiState.value.phase)
        assertEquals(DocumentError.Unreadable, vm.uiState.value.error)
        assertNull(vm.uiState.value.document)
        assertTrue(recents.items.value.isEmpty())
        vm.retry()
        assertEquals(2, engine.opens)
    }

    @Test
    fun editUndoSaveAndExit() = runTest(dispatcher) {
        val engine = FakeEngine()
        val vm = ViewerViewModel("ref", "a.hwp", engine, recents(), io = dispatcher)
        assertTrue(vm.uiState.value.canEdit)
        // 편집 모드가 아니면 길게 눌러도 고르지 않는다
        vm.longPress(0, 5f, 150f)
        assertNull(vm.uiState.value.paragraph)
        vm.toggleEditing()
        assertTrue(vm.uiState.value.editing)
        vm.longPress(0, 5f, 150f)
        assertEquals("둘째 문단", vm.uiState.value.paragraph?.text)
        vm.applyParagraph("고친 문단")
        val edited = vm.uiState.value
        assertNull(edited.paragraph)
        assertEquals(listOf("첫 문단", "고친 문단"), engine.paragraphs)
        assertEquals(4, edited.pageCount)
        assertEquals(4, edited.pageSizes.size)
        assertEquals(1, edited.revision)
        assertTrue(edited.dirty && edited.canUndo)
        // 저장하지 않고 나가려 하면 묻는다 → 저장하면 나간다
        vm.requestExit()
        assertTrue(vm.uiState.value.confirmExit)
        vm.events.test {
            vm.save()
            assertEquals(ViewerEvent.Saved, awaitItem())
            assertEquals(ViewerEvent.Exit, awaitItem())
        }
        assertEquals(listOf("ref"), engine.saved)
        assertFalse(vm.uiState.value.dirty)
        // 되돌리면 한 동작(엔진 2단계)을 통째로 되돌리고, 저장한 상태와 달라진다
        vm.undo()
        assertEquals(0, engine.depth)
        assertTrue(vm.uiState.value.dirty)
        assertFalse(vm.uiState.value.canUndo)
    }

    @Test
    fun failedEditAndSaveAreReported() = runTest(dispatcher) {
        val engine = FakeEngine(saveError = DocumentError.ReadOnly)
        val vm = ViewerViewModel("ref", "a.hwp", engine, recents(), io = dispatcher)
        vm.toggleEditing()
        vm.events.test {
            vm.longPress(0, 0f, 0f)
            engine.paragraphs.clear() // 그 사이 문단이 사라졌다
            vm.applyParagraph("x")
            assertEquals(ViewerEvent.EditFailed, awaitItem())
            vm.save()
            assertEquals(ViewerEvent.SaveFailed(DocumentError.ReadOnly), awaitItem())
        }
        assertNull(vm.uiState.value.paragraph)
        // 변경 없이 나가면 묻지 않는다
        vm.events.test {
            vm.requestExit()
            assertEquals(ViewerEvent.Exit, awaitItem())
        }
    }

    @Test
    fun cursorTypingIsGroupedForUndo() = runTest(dispatcher) {
        val engine = FakeEngine()
        val vm = ViewerViewModel("ref", "a.hwp", engine, recents(), io = dispatcher)
        vm.toggleEditing()
        vm.tap(0, 30f, 5f) // 첫 문단 3번째 글자 뒤
        assertEquals(TextPos(0, 0, 3), vm.uiState.value.caret?.position)
        vm.type("가")
        vm.type("나")
        assertEquals("첫 문가나단", engine.paragraphs[0])
        assertEquals(TextPos(0, 0, 5), vm.uiState.value.caret?.position)
        assertTrue(vm.uiState.value.dirty && vm.uiState.value.canUndo)
        vm.backspace()
        assertEquals("첫 문가단", engine.paragraphs[0])
        vm.moveCaret(CaretMove.LineEnd)
        vm.type("!")
        assertEquals("첫 문가단!", engine.paragraphs[0])
        // 되돌리기: !, 지우기, 이어 친 두 글자 — 세 번이면 처음으로
        vm.undo()
        vm.undo()
        assertTrue(vm.uiState.value.canUndo)
        vm.undo()
        assertEquals(0, engine.depth)
        assertFalse(vm.uiState.value.canUndo)
        // 편집 모드를 끄면 커서가 사라진다
        vm.toggleEditing()
        assertNull(vm.uiState.value.caret)
    }

    @Test
    fun selectionReplaceAndStyles() = runTest(dispatcher) {
        val engine = FakeEngine()
        engine.paragraphs[0] = "hello world"
        val vm = ViewerViewModel("ref", "a.docx", engine, recents(), io = dispatcher)
        vm.toggleEditing()
        // 두 번 누르면 단어
        vm.doubleTap(0, 70f, 5f)
        assertEquals(TextRange(TextPos(0, 0, 6), TextPos(0, 0, 11)), vm.uiState.value.selection)
        assertTrue(vm.uiState.value.selectionBoxes.isNotEmpty())
        // 굵게: 선택에 적용, 선택은 남는다
        vm.toggleStyle(StyleToggle.Bold)
        assertEquals(TextStyle(bold = true), engine.styles.last().second)
        assertEquals(true, vm.uiState.value.style?.bold)
        assertTrue(vm.uiState.value.selection != null)
        vm.changeFontSize(2)
        assertEquals(12f, engine.size)
        // 선택 위에 치면 바꾼다
        vm.type("papy")
        assertEquals("hello papy", engine.paragraphs[0])
        assertNull(vm.uiState.value.selection)
        // 범위 선택: 커서에서 누른 자리까지
        vm.tap(0, 0f, 5f)
        vm.toggleExtending()
        vm.tap(0, 50f, 5f)
        assertEquals(TextRange(TextPos(0, 0, 0), TextPos(0, 0, 5)), vm.uiState.value.selection)
        vm.backspace()
        assertEquals(" papy", engine.paragraphs[0])
        // 선택이 없으면 커서가 든 단어에 모양을 적용한다
        vm.collapseSelection()
        vm.tap(0, 30f, 5f)
        vm.setColor("#ff0000")
        assertEquals(TextRange(TextPos(0, 0, 1), TextPos(0, 0, 5)), engine.styles.last().first)
    }

    @Test
    fun exportsPdf() = runTest(dispatcher) {
        val engine = FakeEngine()
        val vm = ViewerViewModel("ref", "보고서.hwpx", engine, recents(), io = dispatcher)
        assertEquals("보고서.pdf", vm.uiState.value.pdfName)
        assertTrue(vm.uiState.value.canExportPdf)
        vm.events.test {
            vm.exportPdf("target")
            assertEquals(ViewerEvent.Exported("target"), awaitItem())
            engine.exportError = DocumentError.ReadOnly
            vm.exportPdf("other")
            assertEquals(ViewerEvent.ExportFailed(DocumentError.ReadOnly), awaitItem())
        }
        assertEquals(listOf("target"), engine.exported)
        assertFalse(vm.uiState.value.exporting)
    }
}
