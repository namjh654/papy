package io.github.namjh654.papy.feature.documents

import io.github.namjh654.papy.core.data.storage.LocalStorage
import io.github.namjh654.papy.feature.documents.viewer.PageText
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
) : DocumentEngine {
    val closed = mutableListOf<Long>()
    var opens = 0
    override fun open(ref: String, password: String?): OpenResult {
        opens++
        if (error != null) return OpenResult.failure(error)
        if (this.password != null && password != this.password) return OpenResult.failure(DocumentError.PasswordRequired)
        return OpenResult.success(OpenedDocument(session = 7, kind = DocumentKind.Hwp, pageCount = 3, title = null, droppedObjects = 2))
    }
    override fun pageSize(session: Long, page: Int) = if (page == 1) null else PageSizePt(100f + page, 200f)
    override fun pageText(session: Long, page: Int) = if (page == 0) "첫 쪽" else null
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
}
