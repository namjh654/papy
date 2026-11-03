package io.github.namjh654.papy.feature.community.detail

import io.github.namjh654.papy.core.common.error.toAppError
import io.github.namjh654.papy.core.common.viewmodel.StatefulViewModel
import io.github.namjh654.papy.feature.community.Comment
import io.github.namjh654.papy.feature.community.CommunityRepository
import io.github.namjh654.papy.feature.community.PostChange
import io.github.namjh654.papy.feature.community.ReportReason
import io.github.namjh654.papy.feature.community.ReportTarget
import io.github.namjh654.papy.feature.community.withLike
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 글 상세(20-1): 글·댓글, 좋아요(낙관적), 댓글 쓰기·지우기(내 댓글), 내 글 고치기·지우기, 남의 글·댓글 신고, 글쓴이 차단.
 * Swift: `IOSAppGraph.shared.postDetailViewModel(postId:)`
 */
class PostDetailViewModel(
    private val postId: String,
    private val repository: CommunityRepository,
) : StatefulViewModel<PostDetailUiState, PostDetailEvent>(PostDetailUiState()) {

    val uiState: StateFlow<PostDetailUiState> = state
    val events: Flow<PostDetailEvent> = eventFlow

    init {
        load()
        launch { repository.changes.collect(::onChange) }
    }

    /** 처음 불러오기(전체 오류 화면의 다시 시도도 이것) */
    fun load() = launch {
        setState { copy(isLoading = true, error = null) }
        val post = repository.post(postId)
        val comments = repository.comments(postId, page = null).items
        setState { copy(post = post, comments = comments, isLoading = false) }
    }

    fun likeClicked() = action {
        val post = currentState.post ?: return@action
        val liked = !post.likedByMe
        setState { copy(post = post.withLike(liked)) }
        try {
            val result = repository.setLiked(post.id, liked)
            setState { copy(post = this.post?.copy(likeCount = result.likeCount, likedByMe = result.likedByMe)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            setState { copy(post = post) }
            throw e
        }
    }

    fun commentInputChanged(value: String) = setState { copy(commentInput = value) }

    fun sendComment() = action {
        if (!currentState.canSendComment) return@action
        setState { copy(isSendingComment = true) }
        try {
            val comment = repository.addComment(postId, currentState.commentInput)
            setState {
                copy(
                    comments = comments + comment,
                    commentInput = "",
                    post = post?.copy(commentCount = (post.commentCount) + 1),
                )
            }
        } finally {
            setState { copy(isSendingComment = false) }
        }
    }

    fun deleteComment(comment: Comment) = action {
        repository.deleteComment(comment.id)
        setState {
            copy(comments = comments.filterNot { it.id == comment.id }, post = post?.copy(commentCount = (post.commentCount - 1).coerceAtLeast(0)))
        }
    }

    fun editClicked() = launch {
        currentState.post?.takeIf { it.isMine }?.let { sendEvent(PostDetailEvent.EditRequested(it)) }
    }

    fun deletePost() = action {
        repository.delete(postId)
        sendEvent(PostDetailEvent.Closed)
    }

    fun reportPost(reason: ReportReason) = action {
        repository.report(ReportTarget.Post, postId, reason)
        sendEvent(PostDetailEvent.Reported)
    }

    fun reportComment(comment: Comment, reason: ReportReason) = action {
        repository.report(ReportTarget.Comment, comment.id, reason)
        sendEvent(PostDetailEvent.Reported)
    }

    /** 글쓴이(또는 댓글 쓴 사람) 차단 — 글쓴이면 상세를 닫는다 */
    fun block(userId: String) = action {
        repository.block(userId)
    }

    private suspend fun onChange(change: PostChange) {
        when (change) {
            is PostChange.Saved -> if (change.post.id == postId) setState { copy(post = change.post) }
            is PostChange.Deleted -> Unit
            is PostChange.AuthorBlocked -> {
                if (currentState.post?.author?.id == change.authorId) {
                    sendEvent(PostDetailEvent.Closed)
                } else {
                    setState { copy(comments = comments.filterNot { it.author.id == change.authorId }) }
                }
            }
        }
    }

    /** 동작 실패는 화면 전체 오류가 아니라 안내로 */
    private fun action(block: suspend () -> Unit) = launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            sendEvent(PostDetailEvent.ActionFailed(e.toAppError().toUiError()))
        }
    }

    /** 첫 불러오기 실패만 화면 전체 오류로 */
    override fun onError(throwable: Throwable) = setState {
        copy(isLoading = false, error = throwable.toAppError().toUiError())
    }
}
