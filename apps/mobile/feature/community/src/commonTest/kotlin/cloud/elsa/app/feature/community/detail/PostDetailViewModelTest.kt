package cloud.elsa.app.feature.community.detail

import cloud.elsa.app.feature.community.testCommunityCache
import app.cash.turbine.test
import cloud.elsa.app.core.common.error.AppError
import cloud.elsa.app.core.common.error.ErrorCode
import cloud.elsa.app.feature.community.CommunityRepository
import cloud.elsa.app.feature.community.FakeCommunityApi
import cloud.elsa.app.feature.community.ReportReason
import cloud.elsa.app.feature.community.ReportTarget
import cloud.elsa.app.feature.community.alice
import cloud.elsa.app.feature.community.bob
import cloud.elsa.app.feature.community.sampleComment
import cloud.elsa.app.feature.community.samplePost
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
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class PostDetailViewModelTest {

    private val api = FakeCommunityApi().apply {
        detail = samplePost("p1", alice).copy(commentCount = 1)
        commentList = listOf(sampleComment("c1", bob))
    }
    private val repository = CommunityRepository(api, testCommunityCache())
    private val vm by lazy { PostDetailViewModel("p1", repository) }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun loadsPostAndComments() = runTest {
        assertEquals("p1", vm.uiState.value.post?.id)
        assertEquals(listOf("c1"), vm.uiState.value.comments.map { it.id })
    }

    @Test
    fun loadFailureShowsError() = runTest {
        api.error = AppError.Http(404, "")
        assertEquals(ErrorCode.RequestRejected, vm.uiState.value.error?.code)
    }

    @Test
    fun sendingCommentAppendsAndCounts() = runTest {
        vm.commentInputChanged("nice")
        vm.sendComment()

        val state = vm.uiState.value
        assertEquals(listOf("c1", "new"), state.comments.map { it.id })
        assertEquals("", state.commentInput)
        assertEquals(2, state.post?.commentCount)
    }

    @Test
    fun deletingPostCloses() = runTest {
        vm.events.test {
            vm.deletePost()
            assertEquals(PostDetailEvent.Closed, awaitItem())
        }
        assertEquals(listOf("p1"), api.deleted)
    }

    @Test
    fun reportSendsReason() = runTest {
        vm.events.test {
            vm.reportPost(ReportReason.Spam)
            assertEquals(PostDetailEvent.Reported, awaitItem())
        }
        assertEquals(Triple(ReportTarget.Post, "p1", ReportReason.Spam), api.reports.single())
    }

    @Test
    fun blockingAuthorClosesButCommenterOnlyHidesComments() = runTest {
        vm.block(bob.id)
        assertEquals(emptyList(), vm.uiState.value.comments)

        vm.events.test {
            vm.block(alice.id)
            assertEquals(PostDetailEvent.Closed, awaitItem())
        }
    }

    @Test
    fun actionFailureIsReportedNotFullScreen() = runTest {
        vm.uiState.value // 먼저 불러온다
        api.error = AppError.Http(500, "")
        vm.events.test {
            vm.deletePost()
            assertIs<PostDetailEvent.ActionFailed>(awaitItem())
        }
        assertEquals(null, vm.uiState.value.error)
    }
}
