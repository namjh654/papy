package io.github.namjh654.papy.feature.community.detail

import io.github.namjh654.papy.core.common.error.UiError
import io.github.namjh654.papy.core.common.viewmodel.UiState
import io.github.namjh654.papy.feature.community.Comment
import io.github.namjh654.papy.feature.community.Post

/**
 * 글 상세. [error] 는 글을 못 불러왔을 때(전체 오류 화면). 댓글 보내기·삭제 등 동작 실패는 [PostDetailEvent.ActionFailed] 로 알린다.
 */
data class PostDetailUiState(
    val post: Post? = null,
    val comments: List<Comment> = emptyList(),
    val commentInput: String = "",
    val isLoading: Boolean = true,
    val isSendingComment: Boolean = false,
    val error: UiError? = null,
) : UiState {
    val canSendComment: Boolean get() = commentInput.isNotBlank() && !isSendingComment
}
