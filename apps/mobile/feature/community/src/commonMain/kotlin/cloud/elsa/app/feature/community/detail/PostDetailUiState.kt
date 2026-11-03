package cloud.elsa.app.feature.community.detail

import cloud.elsa.app.core.common.error.UiError
import cloud.elsa.app.core.common.viewmodel.UiState
import cloud.elsa.app.feature.community.Comment
import cloud.elsa.app.feature.community.Post

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
