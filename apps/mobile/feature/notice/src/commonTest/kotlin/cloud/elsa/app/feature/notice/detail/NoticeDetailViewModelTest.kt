package cloud.elsa.app.feature.notice.detail

import cloud.elsa.app.core.common.error.AppError
import cloud.elsa.app.core.common.error.ErrorCode
import cloud.elsa.app.core.common.paging.Page
import cloud.elsa.app.feature.notice.NoticeApi
import cloud.elsa.app.feature.notice.NoticeItem
import cloud.elsa.app.feature.notice.NoticeRepository
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

@OptIn(ExperimentalCoroutinesApi::class)
class NoticeDetailViewModelTest {

    private class FakeApi(var fail: Boolean = false) : NoticeApi {
        val requested = mutableListOf<String>()
        override suspend fun items(page: String?): Page<NoticeItem> = error("not used")
        override suspend fun item(id: String): NoticeItem {
            requested += id
            if (fail) throw AppError.Http(500, "")
            return NoticeItem(id = id, title = "Notice $id", body = "body")
        }
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun loadsItemByIdOnStart() = runTest {
        val api = FakeApi()

        val state = NoticeDetailViewModel("7", NoticeRepository(api)).uiState.value

        assertEquals(listOf("7"), api.requested)
        assertEquals("Notice 7", state.item?.title)
        assertFalse(state.isLoading)
    }

    @Test
    fun failureShowsErrorAndRetryLoads() = runTest {
        val api = FakeApi(fail = true)
        val vm = NoticeDetailViewModel("7", NoticeRepository(api))

        assertEquals(ErrorCode.ServerError, vm.uiState.value.error?.code)
        assertNull(vm.uiState.value.item)

        api.fail = false
        vm.load()

        assertEquals("7", vm.uiState.value.item?.id)
        assertNull(vm.uiState.value.error)
    }
}
