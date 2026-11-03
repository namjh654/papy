package cloud.elsa.app.feature.community

import app.cash.turbine.test
import cloud.elsa.app.core.common.error.AppError
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

@OptIn(ExperimentalCoroutinesApi::class)
class CommunityViewModelTest {

    private val api = FakeCommunityApi().apply {
        postsPage = { page -> if (page == null) Page(listOf(samplePost("1", likes = 10), samplePost("2", bob)), nextKey = "2") else Page(listOf(samplePost("3"))) }
    }
    private val repository = CommunityRepository(api, testCommunityCache())
    private val vm by lazy { CommunityViewModel(repository) }
    private val ids get() = vm.uiState.value.list.items.map { it.id }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun loadsAndPages() = runTest {
        assertEquals(listOf("1", "2"), ids)
        vm.loadMore()
        assertEquals(listOf("1", "2", "3"), ids)
    }

    @Test
    fun likeUsesServerCount() = runTest {
        vm.likeClicked(vm.uiState.value.list.items.first())
        val first = vm.uiState.value.list.items.first()
        assertEquals(true, first.likedByMe)
        assertEquals(11, first.likeCount)
    }

    @Test
    fun likeFailureRevertsAndTells() = runTest {
        api.likeError = AppError.Http(500, "")
        vm.events.test {
            vm.likeClicked(vm.uiState.value.list.items.first())
            assertEquals(CommunityEvent.LikeFailed, awaitItem())
        }
        val first = vm.uiState.value.list.items.first()
        assertEquals(false, first.likedByMe)
        assertEquals(10, first.likeCount)
    }

    @Test
    fun changesFromOtherScreensApply() = runTest {
        vm.events.test {
            repository.save(null, "fresh", emptyList())
            assertEquals(CommunityEvent.ScrollToTop, awaitItem())
        }
        assertEquals(listOf("new", "1", "2"), ids)

        repository.delete("1")
        assertEquals(listOf("new", "2"), ids)

        repository.block(bob.id)
        assertEquals(listOf("new"), ids)
    }

    @Test
    fun filterReloadsWithMine() = runTest {
        vm.uiState.value
        api.postsPage = { Page(listOf(samplePost("mine", mine = true))) }

        vm.filterChanged(CommunityFilter.Mine)

        assertEquals(CommunityFilter.Mine, api.lastFilter)
        assertEquals(listOf("mine"), ids)
        assertEquals(CommunityFilter.Mine, vm.uiState.value.filter)
    }
}
