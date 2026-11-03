package cloud.elsa.app.feature.community

import cloud.elsa.app.core.common.paging.Page
import cloud.elsa.app.core.data.media.AttachmentInput
import cloud.elsa.app.core.data.media.AttachmentKind

internal val alice = Author("a", "Alice")
internal val bob = Author("b", "Bob")

internal fun samplePost(id: String, author: Author = alice, likes: Int = 0, liked: Boolean = false, mine: Boolean = false, images: List<String> = emptyList()) =
    Post(id = id, author = author, content = "content $id", imageUrls = images, createdAt = 0, likeCount = likes, likedByMe = liked, isMine = mine)

internal fun sampleComment(id: String, author: Author = bob, mine: Boolean = false) =
    Comment(id = id, postId = "p1", author = author, content = "comment $id", createdAt = 0, isMine = mine)

internal fun image(name: String = "a.jpg") = AttachmentInput(name, "image/jpeg", AttachmentKind.Image, ByteArray(1))

/** 호출을 기록하고 정해 둔 응답을 주는 대역 */
internal class FakeCommunityApi : CommunityApi {
    var postsPage: (String?) -> Page<Post> = { Page(emptyList()) }
    var detail: Post = samplePost("p1")
    var commentList: List<Comment> = emptyList()
    var error: Throwable? = null
    var likeError: Throwable? = null

    val uploaded = mutableListOf<String>()
    val created = mutableListOf<PostRequest>()
    val updated = mutableListOf<Pair<String, PostRequest>>()
    val deleted = mutableListOf<String>()
    val reports = mutableListOf<Triple<ReportTarget, String, ReportReason>>()
    val blockCalls = mutableListOf<Pair<String, Boolean>>()
    var blockedList: List<Author> = emptyList()

    private fun fail() = error?.let { throw it }

    var lastFilter: CommunityFilter? = null
    override suspend fun posts(page: String?, filter: CommunityFilter): Page<Post> { lastFilter = filter; return postsPage(page) }
    override suspend fun post(id: String): Post { fail(); return detail }
    var profile: UserProfile = UserProfile(id = "b", name = "Bob", postCount = 2)
    var userPostsPage: (String?) -> Page<Post> = { Page(emptyList()) }
    override suspend fun user(id: String): UserProfile { fail(); return profile }
    override suspend fun userPosts(userId: String, page: String?) = userPostsPage(page)
    override suspend fun comments(postId: String, page: String?) = Page(commentList)
    override suspend fun upload(input: AttachmentInput): String { fail(); uploaded += input.name; return "https://cdn/${input.name}" }
    override suspend fun create(request: PostRequest): Post { fail(); created += request; return samplePost("new", mine = true).copy(content = request.content, imageUrls = request.imageUrls) }
    override suspend fun update(id: String, request: PostRequest): Post { fail(); updated += id to request; return detail.copy(content = request.content, imageUrls = request.imageUrls) }
    override suspend fun delete(id: String) { fail(); deleted += id }
    override suspend fun setLiked(id: String, liked: Boolean): LikeResponse {
        likeError?.let { throw it }
        return LikeResponse(likeCount = if (liked) 11 else 9, likedByMe = liked)
    }
    override suspend fun addComment(postId: String, content: String): Comment { fail(); return sampleComment("new", mine = true).copy(content = content) }
    override suspend fun deleteComment(id: String) { fail() }
    override suspend fun report(target: ReportTarget, targetId: String, reason: ReportReason) { fail(); reports += Triple(target, targetId, reason) }
    override suspend fun blocked(): List<Author> = blockedList
    override suspend fun setBlocked(userId: String, blocked: Boolean) { fail(); blockCalls += userId to blocked }
}
