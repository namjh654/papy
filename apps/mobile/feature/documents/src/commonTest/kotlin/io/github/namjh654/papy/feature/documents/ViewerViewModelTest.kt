package io.github.namjh654.papy.feature.documents

import io.github.namjh654.papy.core.data.storage.LocalStorage
import app.cash.turbine.test
import io.github.namjh654.papy.feature.documents.viewer.PageText
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
    val undone = mutableListOf<Int>()
    val saved = mutableListOf<String>()
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
        return 2
    }
    override fun undo(session: Long): Boolean { undone += 1; return true }
    override fun save(session: Long, ref: String): DocumentError? { if (saveError == null) saved += ref; return saveError }
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
        vm.uiState.test {
            assertTrue(awaitItem().canEdit)
            // 편집 모드가 아니면 눌러도 고르지 않는다
            vm.pageTapped(0, 5f, 150f)
            vm.toggleEditing()
            assertTrue(awaitItem().editing)
            vm.pageTapped(0, 5f, 150f)
            assertEquals("둘째 문단", awaitItem().selection?.text)
            vm.applyEdit("고친 문단")
            awaitItem() // 고른 문단 닫힘
            val edited = awaitItem()
            assertEquals(listOf("첫 문단", "고친 문단"), engine.paragraphs)
            assertEquals(4, edited.pageCount)
            assertEquals(4, edited.pageSizes.size)
            assertEquals(1, edited.revision)
            assertTrue(edited.dirty && edited.canUndo)

            // 저장하지 않고 나가려 하면 묻는다 → 저장하면 나간다
            vm.requestExit()
            assertTrue(awaitItem().confirmExit)
            cancelAndIgnoreRemainingEvents()
        }
        vm.events.test {
            vm.save()
            assertEquals(ViewerEvent.Saved, awaitItem())
            assertEquals(ViewerEvent.Exit, awaitItem())
        }
        assertEquals(listOf("ref"), engine.saved)
        assertFalse(vm.uiState.value.dirty)
        // 되돌리면 단계 수만큼 엔진을 되돌리고, 저장한 상태와 달라진다
        vm.undo()
        assertEquals(2, engine.undone.size)
        assertTrue(vm.uiState.value.dirty)
        assertFalse(vm.uiState.value.canUndo)
    }

    @Test
    fun failedEditAndSaveAreReported() = runTest(dispatcher) {
        val engine = FakeEngine(saveError = DocumentError.ReadOnly)
        val vm = ViewerViewModel("ref", "a.hwp", engine, recents(), io = dispatcher)
        vm.toggleEditing()
        vm.events.test {
            vm.pageTapped(0, 0f, 0f)
            engine.paragraphs.clear() // 그 사이 문단이 사라졌다
            vm.applyEdit("x")
            assertEquals(ViewerEvent.EditFailed, awaitItem())
            vm.save()
            assertEquals(ViewerEvent.SaveFailed(DocumentError.ReadOnly), awaitItem())
        }
        assertNull(vm.uiState.value.selection)
        // 변경 없이 나가면 묻지 않는다
        vm.events.test {
            vm.requestExit()
            assertEquals(ViewerEvent.Exit, awaitItem())
        }
    }
}
