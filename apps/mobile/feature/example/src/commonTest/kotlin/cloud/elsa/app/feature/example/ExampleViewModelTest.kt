package cloud.elsa.app.feature.example

import app.cash.turbine.test
import cloud.elsa.app.core.common.error.AppError
import cloud.elsa.app.core.common.error.ErrorCode
import cloud.elsa.app.core.common.paging.Page
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
class ExampleViewModelTest {

    private val item = ExampleItem(id = "1", title = "A")

    private class FakeApi(var result: (String?) -> Page<ExampleItem>) : ExampleApi {
        override suspend fun items(page: String?) = result(page)
        override suspend fun item(id: String) = error("not used")
        override suspend fun setFavorite(id: String, favorite: Boolean) = ExampleItem(id = id, title = "A", favorite = favorite)
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
        val vm = ExampleViewModel(ExampleRepository(api))

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
        val vm = ExampleViewModel(ExampleRepository(api))

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
        val vm = ExampleViewModel(ExampleRepository(FakeApi { Page(listOf(item.copy(title = version))) }))

        vm.uiState.test {
            assertEquals("old", awaitItem().list.items.single().title)
            version = "new"
            vm.refresh()
            assertEquals("new", awaitItem().list.items.single().title)
        }
    }

    @Test
    fun clickSendsEvent() = runTest {
        val vm = ExampleViewModel(ExampleRepository(FakeApi { Page(listOf(item)) }))

        vm.events.test {
            vm.itemClicked(item)
            assertEquals(ExampleEvent.ItemSelected("1"), awaitItem())
        }
    }

    @Test
    fun changeFromAnotherScreenUpdatesThatItemOnly() = runTest {
        val repository = ExampleRepository(FakeApi { Page(listOf(item, item.copy(id = "2"))) })
        val vm = ExampleViewModel(repository)

        repository.setFavorite("2", favorite = true) // 상세 화면이 하는 일

        assertEquals(listOf(false, true), vm.uiState.value.list.items.map { it.favorite })
    }
}
