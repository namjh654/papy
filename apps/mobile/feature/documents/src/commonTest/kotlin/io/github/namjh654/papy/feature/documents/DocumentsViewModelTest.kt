package io.github.namjh654.papy.feature.documents

import app.cash.turbine.test
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentsViewModelTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun showsRecentAndOpensFiles() = runTest {
        val recents = RecentDocuments(MemoryStorage(), now = { 5L })
        recents.add("a", "a.hwp", DocumentKind.Hwp)
        recents.add("b", "b.pdf", DocumentKind.Pdf)
        val vm = DocumentsViewModel(recents)
        assertFalse(vm.uiState.value.loading)
        assertEquals(listOf("b", "a"), vm.uiState.value.recent.map { it.ref })

        vm.events.test {
            vm.fileChosen("c", "c.docx")
            assertEquals(DocumentsEvent.Open("c", "c.docx"), awaitItem())
            vm.recentClicked(vm.uiState.value.recent.last())
            assertEquals(DocumentsEvent.Open("a", "a.hwp"), awaitItem())
        }
        vm.removeRecent(vm.uiState.value.recent.first())
        assertEquals(listOf("a"), vm.uiState.value.recent.map { it.ref })
        vm.clearRecent()
        assertTrue(vm.uiState.value.recent.isEmpty())
    }
}
