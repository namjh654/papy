package io.github.namjh654.papy.feature.example.detail

import app.cash.turbine.test
import io.github.namjh654.papy.core.common.error.AppError
import io.github.namjh654.papy.core.common.error.ErrorCode
import io.github.namjh654.papy.core.common.paging.Page
import io.github.namjh654.papy.feature.example.ExampleApi
import io.github.namjh654.papy.feature.example.ExampleItem
import io.github.namjh654.papy.feature.example.ExampleRepository
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

@OptIn(ExperimentalCoroutinesApi::class)
class ExampleDetailViewModelTest {

    private class FakeApi(var failLoad: Boolean = false, var failSave: Boolean = false) : ExampleApi {
        var favorite = false
        override suspend fun items(page: String?): Page<ExampleItem> = error("not used")
        override suspend fun item(id: String): ExampleItem {
            if (failLoad) throw AppError.Http(500, "")
            return ExampleItem(id = id, title = "Item $id", favorite = favorite)
        }
        override suspend fun setFavorite(id: String, favorite: Boolean): ExampleItem {
            if (failSave) throw AppError.Http(400, "")
            this.favorite = favorite
            return item(id)
        }
    }

    private fun viewModel(api: ExampleApi, repository: ExampleRepository = ExampleRepository(api)) =
        ExampleDetailViewModel(id = "5", repository = repository)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun loadsItemByIdOnStart() = runTest {
        val state = viewModel(FakeApi()).uiState.value

        assertEquals("Item 5", state.item?.title)
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun loadFailureShowsErrorAndRetryLoads() = runTest {
        val api = FakeApi(failLoad = true)
        val vm = viewModel(api)
        assertEquals(ErrorCode.ServerError, vm.uiState.value.error?.code)
        assertNull(vm.uiState.value.item)

        api.failLoad = false
        vm.load()

        assertEquals("5", vm.uiState.value.item?.id)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun toggleFavoriteSavesAndAnnouncesToList() = runTest {
        val api = FakeApi()
        val repository = ExampleRepository(api)
        val vm = viewModel(api, repository)

        repository.changes.test {
            vm.toggleFavorite()

            assertTrue(awaitItem().favorite) // 목록 화면이 받는 값
            assertTrue(vm.uiState.value.item!!.favorite)
            assertFalse(vm.uiState.value.isSaving)
        }
    }

    @Test
    fun saveFailureKeepsContentAndSendsEvent() = runTest {
        val vm = viewModel(FakeApi(failSave = true))

        vm.events.test {
            vm.toggleFavorite()

            val event = awaitItem() as ExampleDetailEvent.SaveFailed
            assertEquals(ErrorCode.RequestRejected, event.error.code)
            assertFalse(vm.uiState.value.item!!.favorite) // 내용은 그대로
            assertNull(vm.uiState.value.error)
            assertFalse(vm.uiState.value.isSaving)
        }
    }
}
