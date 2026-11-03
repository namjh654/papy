package cloud.elsa.app.feature.community

import cloud.elsa.app.core.common.error.AppError
import cloud.elsa.app.core.common.error.ErrorCode
import cloud.elsa.app.core.common.paging.Page
import cloud.elsa.app.core.data.media.AttachmentInput
import cloud.elsa.app.feature.community.cache.CommunityCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** 한 화면에서 바꾼 것을 다른 화면(목록·상세)이 다시 불러오지 않고 반영하도록 알린다 */
sealed interface PostChange {
    data class Saved(val post: Post, val isNew: Boolean) : PostChange
    data class Deleted(val postId: String) : PostChange
    data class AuthorBlocked(val authorId: String) : PostChange
}

/** 글에 넣을 사진: 이미 올라간 주소 또는 새로 고른 사진. Swift: `onEnum(of:)` */
sealed interface PostImage {
    data class Remote(val url: String) : PostImage
    data class Local(val key: String, val input: AttachmentInput) : PostImage
}

/**
 * 커뮤니티 데이터. 검증(글 길이·사진 수), 사진 올리기 → 글 저장 순서, 차단한 사용자 걸러 내기(서버가 빼기 전 화면에서 바로),
 * 화면 사이 변경 알림([changes])을 여기서 한다.
 * 로컬 DB([CommunityCache], DEC-130): 전체 목록 첫 페이지를 저장해 다시 켜면 먼저 보이고, 수정·삭제·차단도 저장된 목록에 반영한다.
 */
class CommunityRepository(
    private val api: CommunityApi,
    private val cache: CommunityCache,
) {
    private val _changes = MutableSharedFlow<PostChange>(extraBufferCapacity = 16)
    val changes: SharedFlow<PostChange> = _changes.asSharedFlow()

    private val _blockedIds = MutableStateFlow<Set<String>>(emptySet())
    /** 이번 실행에서 차단한 사용자(서버 목록과 합친다) */
    val blockedIds: StateFlow<Set<String>> = _blockedIds.asStateFlow()

    suspend fun posts(page: String?, filter: CommunityFilter = CommunityFilter.All): Page<Post> {
        val result = api.posts(page, filter)
        val visible = result.copy(items = result.items.filter { it.author.id !in _blockedIds.value })
        if (page == null && filter == CommunityFilter.All) saving { cache.savePosts(visible.items) }
        return visible
    }

    /** 저장된 전체 목록 첫 페이지(받기 전에 보인다). 읽지 못하면 빈 목록 */
    suspend fun cachedPosts(): List<Post> = try {
        cache.posts()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        emptyList()
    }

    suspend fun post(id: String): Post = api.post(id)

    suspend fun user(id: String): UserProfile = api.user(id)

    /** 그 사람이 쓴 글(차단했으면 비어 있다 — 화면은 차단 뒤 닫힌다) */
    suspend fun userPosts(userId: String, page: String?): Page<Post> {
        if (userId in _blockedIds.value) return Page(emptyList())
        return api.userPosts(userId, page)
    }

    suspend fun comments(postId: String, page: String?): Page<Comment> {
        val result = api.comments(postId, page)
        return result.copy(items = result.items.filter { it.author.id !in _blockedIds.value })
    }

    /**
     * 글 저장: 검증 → 새 사진만 올린다(이미 있는 주소는 그대로, 순서 유지) → 만들기/고치기.
     * @param postId null 이면 새 글
     */
    suspend fun save(postId: String?, content: String, images: List<PostImage>): Post {
        val text = content.trim()
        if (text.isEmpty()) throw AppError.Validation(ErrorCode.ContentRequired)
        if (text.length > MAX_CONTENT) throw AppError.Validation(ErrorCode.ContentTooLong)
        if (images.size > MAX_IMAGES) throw AppError.Validation(ErrorCode.TooManyImages)
        val urls = images.map { image ->
            when (image) {
                is PostImage.Remote -> image.url
                is PostImage.Local -> api.upload(image.input)
            }
        }
        val request = PostRequest(content = text, imageUrls = urls)
        val saved = if (postId == null) api.create(request) else api.update(postId, request)
        if (postId != null) saving { cache.update(saved) }
        _changes.emit(PostChange.Saved(saved, isNew = postId == null))
        return saved
    }

    suspend fun delete(postId: String) {
        api.delete(postId)
        saving { cache.remove(postId) }
        _changes.emit(PostChange.Deleted(postId))
    }

    suspend fun setLiked(postId: String, liked: Boolean): LikeResponse = api.setLiked(postId, liked)

    suspend fun addComment(postId: String, content: String): Comment {
        val text = content.trim()
        if (text.isEmpty()) throw AppError.Validation(ErrorCode.ContentRequired)
        if (text.length > MAX_COMMENT) throw AppError.Validation(ErrorCode.ContentTooLong)
        return api.addComment(postId, text)
    }

    suspend fun deleteComment(commentId: String) = api.deleteComment(commentId)

    suspend fun report(target: ReportTarget, targetId: String, reason: ReportReason) = api.report(target, targetId, reason)

    suspend fun blocked(): List<Author> {
        val list = api.blocked()
        _blockedIds.value = _blockedIds.value + list.map { it.id }
        return list
    }

    /** 차단하면 그 사람의 글·댓글이 바로 사라진다(목록·상세가 [PostChange.AuthorBlocked] 를 받는다) */
    suspend fun block(userId: String) {
        api.setBlocked(userId, blocked = true)
        _blockedIds.value = _blockedIds.value + userId
        saving { cache.removeAuthor(userId) }
        _changes.emit(PostChange.AuthorBlocked(userId))
    }

    suspend fun unblock(userId: String) {
        api.setBlocked(userId, blocked = false)
        _blockedIds.value = _blockedIds.value - userId
    }

    /** 로컬 DB 는 보조: 쓰지 못해도 서버 흐름은 계속된다 */
    private suspend fun saving(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
        }
    }

    companion object {
        const val MAX_CONTENT = 2_000
        const val MAX_COMMENT = 500
        const val MAX_IMAGES = 4
    }
}
