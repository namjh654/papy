package cloud.elsa.app.feature.community

import cloud.elsa.app.core.common.paging.Page
import cloud.elsa.app.core.data.media.AttachmentInput
import cloud.elsa.app.core.data.network.BaseApi
import cloud.elsa.app.core.data.network.UploadFile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock

/** 글·댓글을 쓴 사람. Swift: `Author` */
@Serializable
data class Author(
    val id: String,
    val name: String,
    val avatarUrl: String? = null,
)

/**
 * 게시글. [createdAt] 은 epoch 밀리초, [isMine] 은 서버가 로그인 사용자 기준으로 준다(수정·삭제 메뉴).
 * Swift: `Post` — `imageUrls` 는 `[String]`
 */
@Serializable
data class Post(
    val id: String,
    val author: Author,
    val content: String,
    val imageUrls: List<String> = emptyList(),
    val createdAt: Long,
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val likedByMe: Boolean = false,
    val isMine: Boolean = false,
)

@Serializable
data class Comment(
    val id: String,
    val postId: String,
    val author: Author,
    val content: String,
    val createdAt: Long,
    val isMine: Boolean = false,
)

/**
 * 다른 사용자(또는 나)의 프로필(25-1). [isMe] 면 "메시지 보내기"·차단을 숨긴다.
 * Swift: `UserProfile` — `bio` 는 nil 가능
 */
@Serializable
data class UserProfile(
    val id: String,
    val name: String,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val postCount: Int = 0,
    val isMe: Boolean = false,
)

/** 목록 범위(21-2). Swift: `CommunityFilter.all` / `.mine` */
enum class CommunityFilter { All, Mine }

/** 신고 사유(App Store 1.2). Swift: `ReportReason.spam` 등 — 문구는 플랫폼 리소스 */
@Serializable
enum class ReportReason {
    @SerialName("spam") Spam,
    @SerialName("abuse") Abuse,
    @SerialName("inappropriate") Inappropriate,
    @SerialName("other") Other,
}

/** 신고 대상 */
@Serializable
enum class ReportTarget {
    @SerialName("post") Post,
    @SerialName("comment") Comment,
}

@Serializable
data class PostRequest(val content: String, val imageUrls: List<String>)

@Serializable
internal data class CommentRequest(val content: String)

@Serializable
internal data class ReportRequest(val targetType: ReportTarget, val targetId: String, val reason: ReportReason)

@Serializable
internal data class BlockRequest(val userId: String)

@Serializable
data class LikeResponse(val likeCount: Int, val likedByMe: Boolean)

@Serializable
internal data class UploadResponse(val url: String)

@Serializable
internal data class PageResponse<T>(val items: List<T> = emptyList(), val nextPage: String? = null)

/**
 * 커뮤니티 API 계약(가정, docs/known-limitations.md §1). 서버 연동은 [RemoteCommunityApi], 서버 없이 개발할 때는 [SampleCommunityApi].
 * 어느 쪽을 쓸지는 di/CommunityModule.kt 한 곳에서 정한다.
 */
interface CommunityApi {
    /** `GET /api/posts?page=N[&mine=true]` → `{items, nextPage}` (최신순, 서버가 차단한 사용자의 글은 뺀다) */
    suspend fun posts(page: String?, filter: CommunityFilter = CommunityFilter.All): Page<Post>

    /** `GET /api/posts/{id}` */
    suspend fun post(id: String): Post

    /** `GET /api/users/{id}` → [UserProfile] (25-1) */
    suspend fun user(id: String): UserProfile

    /** `GET /api/users/{id}/posts?page=N` → `{items, nextPage}` (그 사람이 쓴 글, 최신순) */
    suspend fun userPosts(userId: String, page: String?): Page<Post>

    /** `GET /api/posts/{id}/comments?page=N` → `{items, nextPage}` (오래된 순) */
    suspend fun comments(postId: String, page: String?): Page<Comment>

    /** `POST /api/uploads` (multipart `file`) → `{url}` — 글 사진은 먼저 올리고 주소를 글에 담는다 */
    suspend fun upload(input: AttachmentInput): String

    /** `POST /api/posts {content, imageUrls}` */
    suspend fun create(request: PostRequest): Post

    /** `PUT /api/posts/{id} {content, imageUrls}` */
    suspend fun update(id: String, request: PostRequest): Post

    /** `DELETE /api/posts/{id}` → 204 */
    suspend fun delete(id: String)

    /** `POST`(좋아요) / `DELETE`(취소) `/api/posts/{id}/like` → `{likeCount, likedByMe}` */
    suspend fun setLiked(id: String, liked: Boolean): LikeResponse

    /** `POST /api/posts/{id}/comments {content}` */
    suspend fun addComment(postId: String, content: String): Comment

    /** `DELETE /api/comments/{id}` → 204 */
    suspend fun deleteComment(id: String)

    /** `POST /api/reports {targetType, targetId, reason}` → 204 */
    suspend fun report(target: ReportTarget, targetId: String, reason: ReportReason)

    /** `GET /api/blocks` → `[Author]` */
    suspend fun blocked(): List<Author>

    /** `POST /api/blocks {userId}` / `DELETE /api/blocks/{userId}` → 204 */
    suspend fun setBlocked(userId: String, blocked: Boolean)
}

internal class RemoteCommunityApi(
    private val baseApi: BaseApi,
) : CommunityApi {

    override suspend fun posts(page: String?, filter: CommunityFilter): Page<Post> {
        val query = buildMap {
            page?.let { put("page", it) }
            if (filter == CommunityFilter.Mine) put("mine", "true")
        }
        val response: PageResponse<Post> = baseApi.get("/api/posts", query = query)
        return Page(items = response.items, nextKey = response.nextPage)
    }

    override suspend fun post(id: String): Post = baseApi.get("/api/posts/$id")

    override suspend fun user(id: String): UserProfile = baseApi.get("/api/users/$id")

    override suspend fun userPosts(userId: String, page: String?): Page<Post> {
        val response: PageResponse<Post> = baseApi.get("/api/users/$userId/posts", query = page?.let { mapOf("page" to it) }.orEmpty())
        return Page(items = response.items, nextKey = response.nextPage)
    }

    override suspend fun comments(postId: String, page: String?): Page<Comment> {
        val response: PageResponse<Comment> = baseApi.get("/api/posts/$postId/comments", query = page?.let { mapOf("page" to it) }.orEmpty())
        return Page(items = response.items, nextKey = response.nextPage)
    }

    override suspend fun upload(input: AttachmentInput): String =
        baseApi.upload<UploadResponse>("/api/uploads", files = listOf(UploadFile("file", input.name, input.mimeType, input.bytes))).url

    override suspend fun create(request: PostRequest): Post = baseApi.post("/api/posts", body = request)

    override suspend fun update(id: String, request: PostRequest): Post = baseApi.put("/api/posts/$id", body = request)

    override suspend fun delete(id: String) {
        baseApi.delete<Unit>("/api/posts/$id")
    }

    override suspend fun setLiked(id: String, liked: Boolean): LikeResponse =
        if (liked) baseApi.post("/api/posts/$id/like", body = Unit) else baseApi.delete("/api/posts/$id/like")

    override suspend fun addComment(postId: String, content: String): Comment =
        baseApi.post("/api/posts/$postId/comments", body = CommentRequest(content))

    override suspend fun deleteComment(id: String) {
        baseApi.delete<Unit>("/api/comments/$id")
    }

    override suspend fun report(target: ReportTarget, targetId: String, reason: ReportReason) {
        baseApi.post<ReportRequest, Unit>("/api/reports", body = ReportRequest(target, targetId, reason))
    }

    override suspend fun blocked(): List<Author> = baseApi.get("/api/blocks")

    override suspend fun setBlocked(userId: String, blocked: Boolean) {
        if (blocked) baseApi.post<BlockRequest, Unit>("/api/blocks", body = BlockRequest(userId)) else baseApi.delete<Unit>("/api/blocks/$userId")
    }
}

/**
 * 서버 없이 화면을 확인하기 위한 예시 데이터(서버 응답 대용이라 문구 리소스가 아니다). 글 25개(10개씩), 첫 글은 내 글, 일부는 사진.
 * 사진은 올린 척만 하고 빈 주소를 준다 → 화면은 회색 자리(미리보기는 작성 화면에서만).
 */
internal class SampleCommunityApi(
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : CommunityApi {

    private val me = Author(id = "me", name = "Me")
    private val people = listOf(Author("u1", "Elsa"), Author("u2", "Anna"), Author("u3", "Olaf"))
    private var sequence = 100
    private val blocked = mutableSetOf<String>()

    private val posts: MutableList<Post> = MutableList(25) { i ->
        val author = if (i == 0) me else people[i % people.size]
        Post(
            id = "p$i",
            author = author,
            content = "Sample post ${i + 1}. " + "This is what a longer post looks like in the list. ".repeat(if (i % 3 == 0) 3 else 1),
            imageUrls = if (i % 4 == 1) List(i % 3 + 1) { "" } else emptyList(),
            createdAt = now() - i * 47 * MINUTE,
            likeCount = (i * 7) % 23,
            commentCount = if (i == 0) 2 else i % 5,
            isMine = author == me,
        )
    }.toMutableList()
    private val comments = mutableMapOf<String, MutableList<Comment>>()

    private fun commentsOf(postId: String) = comments.getOrPut(postId) {
        val count = posts.firstOrNull { it.id == postId }?.commentCount ?: 0
        MutableList(count) { i ->
            val author = people[(i + 1) % people.size]
            Comment(id = "$postId-c$i", postId = postId, author = author, content = "Sample comment ${i + 1}", createdAt = now() - (count - i) * MINUTE)
        }
    }

    override suspend fun posts(page: String?, filter: CommunityFilter): Page<Post> {
        val visible = posts.filter { it.author.id !in blocked && (filter == CommunityFilter.All || it.isMine) }
        val start = page?.toIntOrNull() ?: 0
        val end = (start + PAGE_SIZE).coerceAtMost(visible.size)
        return Page(items = visible.subList(start, end).toList(), nextKey = if (end < visible.size) "$end" else null)
    }

    override suspend fun post(id: String): Post = posts.first { it.id == id }

    override suspend fun user(id: String): UserProfile {
        val author = (people + me).first { it.id == id }
        return UserProfile(
            id = author.id,
            name = author.name,
            avatarUrl = author.avatarUrl,
            bio = if (author == me) null else "Hello, I'm ${author.name}.",
            postCount = posts.count { it.author.id == id },
            isMe = author == me,
        )
    }

    override suspend fun userPosts(userId: String, page: String?): Page<Post> {
        val mine = posts.filter { it.author.id == userId }
        val start = page?.toIntOrNull() ?: 0
        val end = (start + PAGE_SIZE).coerceAtMost(mine.size)
        return Page(items = mine.subList(start, end).toList(), nextKey = if (end < mine.size) "$end" else null)
    }

    override suspend fun comments(postId: String, page: String?): Page<Comment> =
        Page(items = commentsOf(postId).filter { it.author.id !in blocked }, nextKey = null)

    override suspend fun upload(input: AttachmentInput): String = ""

    override suspend fun create(request: PostRequest): Post {
        val post = Post(id = "p${sequence++}", author = me, content = request.content, imageUrls = request.imageUrls, createdAt = now(), isMine = true)
        posts.add(0, post)
        return post
    }

    override suspend fun update(id: String, request: PostRequest): Post {
        val index = posts.indexOfFirst { it.id == id }
        val updated = posts[index].copy(content = request.content, imageUrls = request.imageUrls)
        posts[index] = updated
        return updated
    }

    override suspend fun delete(id: String) {
        posts.removeAll { it.id == id }
    }

    override suspend fun setLiked(id: String, liked: Boolean): LikeResponse {
        val index = posts.indexOfFirst { it.id == id }
        val post = posts[index]
        val count = (post.likeCount + if (liked == post.likedByMe) 0 else if (liked) 1 else -1).coerceAtLeast(0)
        posts[index] = post.copy(likeCount = count, likedByMe = liked)
        return LikeResponse(count, liked)
    }

    override suspend fun addComment(postId: String, content: String): Comment {
        val comment = Comment(id = "c${sequence++}", postId = postId, author = me, content = content, createdAt = now(), isMine = true)
        commentsOf(postId) += comment
        val index = posts.indexOfFirst { it.id == postId }
        posts[index] = posts[index].copy(commentCount = posts[index].commentCount + 1)
        return comment
    }

    override suspend fun deleteComment(id: String) {
        comments.values.forEach { list ->
            val removed = list.firstOrNull { it.id == id } ?: return@forEach
            list.remove(removed)
            val index = posts.indexOfFirst { it.id == removed.postId }
            if (index >= 0) posts[index] = posts[index].copy(commentCount = (posts[index].commentCount - 1).coerceAtLeast(0))
        }
    }

    override suspend fun report(target: ReportTarget, targetId: String, reason: ReportReason) = Unit

    override suspend fun blocked(): List<Author> = people.filter { it.id in blocked }

    override suspend fun setBlocked(userId: String, blocked: Boolean) {
        if (blocked) this.blocked += userId else this.blocked -= userId
    }

    private companion object {
        const val PAGE_SIZE = 10
        const val MINUTE = 60_000L
    }
}
