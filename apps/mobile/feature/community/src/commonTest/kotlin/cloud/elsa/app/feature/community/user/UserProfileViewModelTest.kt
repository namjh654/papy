package cloud.elsa.app.feature.community.user

import app.cash.turbine.test
import cloud.elsa.app.core.common.error.AppError
import cloud.elsa.app.core.common.error.ErrorCode
import cloud.elsa.app.core.common.paging.Page
import cloud.elsa.app.feature.community.CommunityRepository
import cloud.elsa.app.feature.community.FakeCommunityApi
import cloud.elsa.app.feature.community.UserProfile
import cloud.elsa.app.feature.community.bob
import cloud.elsa.app.feature.community.samplePost
import cloud.elsa.app.feature.community.testCommunityCache
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
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class UserProfileViewModelTest {

    private val api = FakeCommunityApi().apply {
        profile = UserProfile(id = "b", name = "Bob", postCount = 2)
        userPostsPage = { Page(listOf(samplePost("p1", bob), samplePost("p2", bob))) }
    }
    private val repository = CommunityRepository(api, testCommunityCache())
    private val vm by lazy { UserProfileViewModel("b", repository) }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun loadsProfileAndPosts() = runTest {
        val state = vm.uiState.value
        assertEquals("Bob", state.profile?.name)
        assertEquals(listOf("p1", "p2"), state.posts.items.map { it.id })
        assertTrue(state.canInteract)
        assertFalse(state.isLoading)
    }

    @Test
    fun profileFailureShowsFullError() = runTest {
        api.error = AppError.Http(404, "")
        assertEquals(ErrorCode.RequestRejected, vm.uiState.value.error?.code)
    }

    @Test
    fun messageRequestsChatWithThatUser() = runTest {
        vm.events.test {
            vm.messageClicked()
            assertEquals(UserProfileEvent.MessageRequested("b", "Bob"), awaitItem())
        }
    }

    @Test
    fun myProfileHasNoMessageOrBlock() = runTest {
        api.profile = UserProfile(id = "me", name = "Me", isMe = true)
        vm.events.test {
            vm.messageClicked()
            vm.block()
            expectNoEvents()
        }
        assertFalse(vm.uiState.value.canInteract)
        assertTrue(api.blockCalls.isEmpty())
    }

    @Test
    fun blockingClosesProfile() = runTest {
        vm.events.test {
            vm.block()
            assertEquals(UserProfileEvent.Blocked, awaitItem())
        }
        assertEquals(listOf("b" to true), api.blockCalls)
    }

    @Test
    fun deletedPostLeavesListAndCount() = runTest {
        vm.uiState.value // 불러오기
        repository.delete("p1")
        assertEquals(listOf("p2"), vm.uiState.value.posts.items.map { it.id })
        assertEquals(1, vm.uiState.value.profile?.postCount)
    }

    @Test
    fun failedLikeRevertsAndTells() = runTest {
        api.likeError = AppError.Http(500, "")
        val post = vm.uiState.value.posts.items.first()
        vm.events.test {
            vm.likeClicked(post)
            assertIs<UserProfileEvent.ActionFailed>(awaitItem())
        }
        assertEquals(post, vm.uiState.value.posts.items.first())
    }
}
