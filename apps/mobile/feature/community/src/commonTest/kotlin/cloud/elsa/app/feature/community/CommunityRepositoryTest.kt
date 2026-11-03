package cloud.elsa.app.feature.community

import app.cash.turbine.test
import cloud.elsa.app.core.common.error.AppError
import cloud.elsa.app.core.common.error.ErrorCode
import cloud.elsa.app.core.common.paging.Page
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CommunityRepositoryTest {

    private val api = FakeCommunityApi()
    private val cache = testCommunityCache()
    private val repository = CommunityRepository(api, cache)

    @Test
    fun saveValidatesContentAndImageCount() = runTest {
        assertEquals(ErrorCode.ContentRequired, assertFailsWith<AppError.Validation> { repository.save(null, "  ", emptyList()) }.code)
        val long = "x".repeat(CommunityRepository.MAX_CONTENT + 1)
        assertEquals(ErrorCode.ContentTooLong, assertFailsWith<AppError.Validation> { repository.save(null, long, emptyList()) }.code)
        val five = List(5) { PostImage.Local("k$it", image()) }
        assertEquals(ErrorCode.TooManyImages, assertFailsWith<AppError.Validation> { repository.save(null, "hi", five) }.code)
        assertTrue(api.created.isEmpty())
    }

    @Test
    fun uploadsOnlyNewImagesAndKeepsOrder() = runTest {
        val images = listOf(PostImage.Remote("https://old/1"), PostImage.Local("k", image("n.jpg")), PostImage.Remote("https://old/2"))

        repository.save("p1", " hello ", images)

        assertEquals(listOf("n.jpg"), api.uploaded)
        assertEquals(PostRequest("hello", listOf("https://old/1", "https://cdn/n.jpg", "https://old/2")), api.updated.single().second)
    }

    @Test
    fun savingAnnouncesChange() = runTest {
        repository.changes.test {
            repository.save(null, "new post", emptyList())
            val change = awaitItem() as PostChange.Saved
            assertTrue(change.isNew)
            assertEquals("new post", change.post.content)
        }
    }

    @Test
    fun blockingHidesAuthorEverywhere() = runTest {
        api.postsPage = { Page(listOf(samplePost("1", alice), samplePost("2", bob))) }
        api.commentList = listOf(sampleComment("c1", alice), sampleComment("c2", bob))
        repository.changes.test {
            repository.block(bob.id)
            assertEquals(PostChange.AuthorBlocked(bob.id), awaitItem())
        }

        assertEquals(listOf("1"), repository.posts(null).items.map { it.id })
        assertEquals(listOf("c1"), repository.comments("p1", null).items.map { it.id })
        assertEquals(listOf(bob.id to true), api.blockCalls)
    }

    @Test
    fun unblockShowsAgain() = runTest {
        api.postsPage = { Page(listOf(samplePost("2", bob))) }
        repository.block(bob.id)
        repository.unblock(bob.id)
        assertEquals(listOf("2"), repository.posts(null).items.map { it.id })
    }

    @Test
    fun commentIsTrimmedAndValidated() = runTest {
        assertEquals(ErrorCode.ContentRequired, assertFailsWith<AppError.Validation> { repository.addComment("p1", " ") }.code)
        assertEquals("hi", repository.addComment("p1", " hi ").content)
    }

    // ---------------------------------------------------------------- 로컬 DB(DEC-130)

    @Test
    fun firstPageOfAllIsSavedAndFollowsEditsDeletesAndBlocks() = runTest {
        api.postsPage = { Page(listOf(samplePost("p1", alice), samplePost("p2", bob), samplePost("p3", alice))) }
        repository.posts(null, CommunityFilter.All)
        repository.posts(null, CommunityFilter.Mine) // 내 글 범위는 저장하지 않는다
        assertEquals(listOf("p1", "p2", "p3"), repository.cachedPosts().map { it.id })

        api.detail = samplePost("p2", bob)
        repository.save("p2", "edited", emptyList())
        assertEquals("edited", repository.cachedPosts().first { it.id == "p2" }.content)

        repository.delete("p2")
        repository.block(alice.id)
        assertTrue(repository.cachedPosts().isEmpty())
    }

    @Test
    fun clearRemovesSavedPosts() = runTest {
        api.postsPage = { Page(listOf(samplePost("p1"))) }
        repository.posts(null)
        cache.clear()
        assertTrue(repository.cachedPosts().isEmpty())
    }
}
