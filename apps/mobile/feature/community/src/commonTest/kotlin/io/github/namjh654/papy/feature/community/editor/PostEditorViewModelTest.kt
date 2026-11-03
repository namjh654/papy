package io.github.namjh654.papy.feature.community.editor

import io.github.namjh654.papy.feature.community.testCommunityCache
import app.cash.turbine.test
import io.github.namjh654.papy.core.common.error.AppError
import io.github.namjh654.papy.core.common.error.ErrorCode
import io.github.namjh654.papy.feature.community.CommunityRepository
import io.github.namjh654.papy.feature.community.FakeCommunityApi
import io.github.namjh654.papy.feature.community.PostImage
import io.github.namjh654.papy.feature.community.image
import io.github.namjh654.papy.feature.community.samplePost
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PostEditorViewModelTest {

    private val api = FakeCommunityApi()
    private val repository = CommunityRepository(api, testCommunityCache())
    private var keys = 0

    private fun editor(postId: String? = null) = PostEditorViewModel(postId, repository, newKey = { "k${++keys}" })

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun newPostNeedsContentAndSaves() = runTest {
        val vm = editor()
        assertFalse(vm.uiState.value.canSave)

        vm.contentChanged("hello")
        assertTrue(vm.uiState.value.canSave)
        vm.events.test {
            vm.save()
            assertIs<PostEditorEvent.Saved>(awaitItem())
        }
        assertEquals("hello", api.created.single().content)
    }

    @Test
    fun imagesStopAtMaximum() = runTest {
        val vm = editor()
        repeat(4) { assertEquals("k${it + 1}", vm.addImage(image())) }
        assertNull(vm.addImage(image()))
        assertFalse(vm.uiState.value.canAddImage)

        vm.removeImage(0)
        assertEquals(listOf("k2", "k3", "k4"), vm.uiState.value.images.map { (it as PostImage.Local).key })
    }

    @Test
    fun editingLoadsExistingPost() = runTest {
        api.detail = samplePost("p1", mine = true, images = listOf("https://old/1"))
        val vm = editor("p1")

        assertTrue(vm.uiState.value.isEditing)
        assertEquals("content p1", vm.uiState.value.content)
        assertEquals(listOf(PostImage.Remote("https://old/1")), vm.uiState.value.images)
    }

    @Test
    fun saveFailureShowsErrorAndKeepsInput() = runTest {
        val vm = editor()
        vm.contentChanged("hello")
        api.error = AppError.Http(500, "")

        vm.save()

        assertEquals(ErrorCode.ServerError, vm.uiState.value.error?.code)
        assertFalse(vm.uiState.value.isSaving)
        assertEquals("hello", vm.uiState.value.content)
    }
}
