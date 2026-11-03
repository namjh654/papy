package cloud.elsa.app.core.common.paging

import app.cash.turbine.test
import cloud.elsa.app.core.common.error.AppError
import cloud.elsa.app.core.common.error.ErrorCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PaginatorTest {

    /** "1".."n" 페이지, 페이지마다 2개 */
    private fun pages(last: Int, fail: (String?) -> Boolean = { false }): suspend (String?) -> Page<String> = { key ->
        if (fail(key)) throw AppError.Http(500, "")
        val page = key?.toInt() ?: 1
        Page(items = listOf("$page-a", "$page-b"), nextKey = if (page < last) "${page + 1}" else null)
    }

    @Test
    fun loadThenLoadMoreUntilEnd() = runTest {
        val paginator = Paginator(load = pages(last = 2))

        paginator.state.test {
            assertEquals(ListState(), awaitItem())
            paginator.load()
            assertTrue(awaitItem().isLoading)
            assertEquals(listOf("1-a", "1-b"), awaitItem().items)

            paginator.loadMore()
            assertTrue(awaitItem().isLoadingMore)
            val last = awaitItem()
            assertEquals(4, last.items.size)
            assertTrue(last.endReached)
            assertFalse(last.canLoadMore)
        }
    }

    @Test
    fun firstPageErrorShowsFullErrorAndRetryRecovers() = runTest {
        var failing = true
        val paginator = Paginator(load = pages(last = 1) { failing })

        paginator.load()
        assertTrue(paginator.state.value.showFullError)
        assertEquals(ErrorCode.ServerError, paginator.state.value.error?.code)

        failing = false
        paginator.load()
        assertNull(paginator.state.value.error)
        assertEquals(2, paginator.state.value.items.size)
    }

    @Test
    fun loadMoreErrorKeepsItemsAndRetryContinues() = runTest {
        var failPage2 = true
        val paginator = Paginator(load = pages(last = 2) { it == "2" && failPage2 })
        paginator.load()

        paginator.loadMore()
        assertEquals(2, paginator.state.value.items.size)
        assertEquals(ErrorCode.ServerError, paginator.state.value.loadMoreError?.code)
        assertFalse(paginator.state.value.canLoadMore)

        failPage2 = false
        paginator.retryLoadMore()
        assertEquals(4, paginator.state.value.items.size)
    }

    @Test
    fun refreshReplacesItemsAndDropsInFlightLoadMore() = runTest {
        val gate = CompletableDeferred<Unit>()
        var version = "v1"
        val paginator = Paginator { key: String? ->
            if (key == "2") gate.await()
            Page(items = listOf("$version-${key ?: 1}"), nextKey = if (key == null) "2" else null)
        }
        paginator.load()

        val more = launch { paginator.loadMore() } // 2페이지가 gate 에서 멈춤
        testScheduler.advanceUntilIdle()
        assertTrue(paginator.state.value.isLoadingMore)

        version = "v2"
        paginator.refresh()
        gate.complete(Unit)
        more.join()

        assertEquals(listOf("v2-1"), paginator.state.value.items) // 늦게 끝난 v1 2페이지는 버려짐
        assertFalse(paginator.state.value.isRefreshing)
    }

    @Test
    fun updateChangesLoadedItemsAndKeepsPaging() = runTest {
        val paginator = Paginator(load = pages(last = 2))
        paginator.load()

        paginator.update { items -> items.map { if (it == "1-b") "changed" else it } }
        assertEquals(listOf("1-a", "changed"), paginator.state.value.items)

        paginator.loadMore() // 다음 페이지 위치는 그대로
        assertEquals(listOf("1-a", "changed", "2-a", "2-b"), paginator.state.value.items)
    }

    @Test
    fun restartClearsItemsAndDropsStaleResult() = runTest {
        val gate = CompletableDeferred<Unit>()
        var query = "a"
        val paginator = Paginator<String> { _ ->
            val asked = query
            if (asked == "a") gate.await()
            Page(items = listOf(asked))
        }

        val first = launch { paginator.restart() } // "a" 가 gate 에서 멈춤
        testScheduler.advanceUntilIdle()
        assertTrue(paginator.state.value.isLoading)

        query = "b"
        paginator.restart()
        gate.complete(Unit)
        first.join()

        assertEquals(listOf("b"), paginator.state.value.items) // 늦게 끝난 "a" 는 버려짐

        paginator.clear()
        assertEquals(ListState(), paginator.state.value)
    }

    @Test
    fun emptyFirstPageIsEmptyState() = runTest {
        val paginator = Paginator<String> { Page(emptyList()) }
        paginator.load()
        assertTrue(paginator.state.value.isEmpty)
        assertTrue(paginator.state.value.endReached)
    }

    @Test
    fun cachedFirstPageShowsWhileRefreshingThenReplaced() = runTest {
        val paginator = Paginator(cache = { listOf("old") }, load = pages(last = 2))

        paginator.state.test {
            assertEquals(ListState(), awaitItem())
            paginator.load()
            assertTrue(awaitItem().isLoading)
            val cached = awaitItem()
            assertEquals(listOf("old"), cached.items)
            assertTrue(cached.isRefreshing)
            assertEquals(listOf("1-a", "1-b"), awaitItem().items)
        }
    }

    @Test
    fun offlineKeepsCachedItemsWithInlineError() = runTest {
        val paginator = Paginator(cache = { listOf("old") }, load = pages(last = 1, fail = { true }))
        paginator.load()

        val state = paginator.state.value
        assertEquals(listOf("old"), state.items)
        assertFalse(state.isRefreshing)
        assertFalse(state.showFullError) // 목록 위에 짧게 알린다
        assertTrue(state.error != null)
    }
}
