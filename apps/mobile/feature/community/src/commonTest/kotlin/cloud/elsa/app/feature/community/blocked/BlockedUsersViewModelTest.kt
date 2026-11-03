package cloud.elsa.app.feature.community.blocked

import cloud.elsa.app.feature.community.testCommunityCache
import app.cash.turbine.test
import cloud.elsa.app.feature.community.CommunityRepository
import cloud.elsa.app.feature.community.FakeCommunityApi
import cloud.elsa.app.feature.community.alice
import cloud.elsa.app.feature.community.bob
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
class BlockedUsersViewModelTest {

    private val api = FakeCommunityApi().apply { blockedList = listOf(alice, bob) }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun listsAndUnblocks() = runTest {
        val vm = BlockedUsersViewModel(CommunityRepository(api, testCommunityCache()))
        assertEquals(listOf(alice, bob), vm.uiState.value.users)

        vm.events.test {
            vm.unblock(bob)
            assertEquals(BlockedUsersEvent.Unblocked(bob), awaitItem())
        }
        assertEquals(listOf(alice), vm.uiState.value.users)
        assertEquals(listOf(bob.id to false), api.blockCalls)
    }
}
