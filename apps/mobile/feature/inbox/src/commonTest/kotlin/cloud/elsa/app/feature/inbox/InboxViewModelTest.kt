package cloud.elsa.app.feature.inbox

import app.cash.turbine.test
import cloud.elsa.app.core.common.error.AppError
import cloud.elsa.app.core.common.paging.Page
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class InboxViewModelTest {

    private class FakeApi(var failMark: Boolean = false) : InboxApi {
        val marked = mutableListOf<String>()
        var allMarked = 0
        override suspend fun items(page: String?) = Page(
            listOf(
                InboxItem(id = "1", title = "Linked", link = "notice/3"),
                InboxItem(id = "2", title = "Plain"),
                InboxItem(id = "3", title = "Read", read = true, link = "settings"),
            ),
        )
        override suspend fun markRead(id: String) {
            if (failMark) throw AppError.Http(500, "")
            marked += id
        }
        override suspend fun markAllRead() {
            allMarked++
        }
    }

    private val api = FakeApi()
    private val viewModel by lazy { InboxViewModel(InboxRepository(api)) }

    private fun item(id: String) = viewModel.uiState.value.list.items.first { it.id == id }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun clickMarksReadAndOpensLink() = runTest {
        viewModel.events.test {
            viewModel.itemClicked(item("1"))
            assertEquals(InboxEvent.Open("notice/3"), awaitItem())
        }
        assertTrue(item("1").read)
        assertEquals(listOf("1"), api.marked)
    }

    @Test
    fun clickWithoutLinkOnlyMarksRead() = runTest {
        viewModel.events.test {
            viewModel.itemClicked(item("2"))
            expectNoEvents()
        }
        assertTrue(item("2").read)
    }

    @Test
    fun alreadyReadItemIsNotSentAgain() = runTest {
        viewModel.itemClicked(item("3"))

        assertTrue(api.marked.isEmpty())
    }

    @Test
    fun markReadFailureKeepsOptimisticState() = runTest {
        api.failMark = true

        viewModel.itemClicked(item("2"))

        assertTrue(item("2").read) // 되돌리지 않는다
        assertEquals(null, viewModel.uiState.value.list.error)
    }

    @Test
    fun markAllReadUpdatesEveryItemOnce() = runTest {
        assertTrue(viewModel.uiState.value.hasUnread)

        viewModel.markAllRead()
        viewModel.markAllRead() // 안 읽은 것이 없으면 다시 보내지 않는다

        assertFalse(viewModel.uiState.value.hasUnread)
        assertEquals(1, api.allMarked)
    }
}
