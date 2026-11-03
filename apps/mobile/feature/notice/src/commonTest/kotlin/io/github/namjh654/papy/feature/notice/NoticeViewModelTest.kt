package io.github.namjh654.papy.feature.notice

import app.cash.turbine.test
import io.github.namjh654.papy.core.common.error.AppError
import io.github.namjh654.papy.core.common.error.ErrorCode
import io.github.namjh654.papy.core.common.paging.Page
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NoticeViewModelTest {

    private val item = NoticeItem(id = "1", title = "A")

    private class FakeApi(var result: (String?) -> Page<NoticeItem>) : NoticeApi {
        override suspend fun items(page: String?) = result(page)
        override suspend fun item(id: String) = error("not used")
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun loadsFirstPageOnStartAndMoreAtEnd() = runTest {
        val api = FakeApi { page ->
            if (page == null) Page(listOf(item), nextKey = "2") else Page(listOf(item.copy(id = "2")))
        }
        val vm = NoticeViewModel(NoticeRepository(api))

        vm.uiState.test {
            assertEquals(listOf(item), awaitItem().list.items)
            vm.loadMore() // Unconfined: 중간 상태(isLoadingMore)는 StateFlow 에서 합쳐진다 — 결과만 본다
            val loaded = awaitItem().list
            assertEquals(listOf("1", "2"), loaded.items.map { it.id })
            assertTrue(loaded.endReached)
        }
    }

    @Test
    fun failureShowsFullErrorAndRetryRecovers() = runTest {
        val api = FakeApi { throw AppError.Http(503, "") }
        val vm = NoticeViewModel(NoticeRepository(api))

        vm.uiState.test {
            val failed = awaitItem().list
            assertTrue(failed.showFullError)
            assertEquals(ErrorCode.ServerError, failed.error?.code)

            api.result = { Page(emptyList()) }
            vm.load()
            assertTrue(awaitItem().list.isEmpty)
        }
    }

    @Test
    fun refreshReplacesItems() = runTest {
        var version = "old"
        val vm = NoticeViewModel(NoticeRepository(FakeApi { Page(listOf(item.copy(title = version))) }))

        vm.uiState.test {
            assertEquals("old", awaitItem().list.items.single().title)
            version = "new"
            vm.refresh()
            assertEquals("new", awaitItem().list.items.single().title)
        }
    }

    @Test
    fun clickSendsEvent() = runTest {
        val vm = NoticeViewModel(NoticeRepository(FakeApi { Page(listOf(item)) }))

        vm.events.test {
            vm.itemClicked(item)
            assertEquals(NoticeEvent.ItemSelected("1"), awaitItem())
        }
    }
}
