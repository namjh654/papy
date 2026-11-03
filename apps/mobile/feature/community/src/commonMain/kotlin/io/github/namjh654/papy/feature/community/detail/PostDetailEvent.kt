package io.github.namjh654.papy.feature.community.detail

import io.github.namjh654.papy.core.common.error.UiError
import io.github.namjh654.papy.core.common.viewmodel.UiEvent
import io.github.namjh654.papy.feature.community.Post

sealed interface PostDetailEvent : UiEvent {
    /** 내 글 고치기 */
    data class EditRequested(val post: Post) : PostDetailEvent

    /** 글을 지웠다 / 글쓴이를 차단했다 — 목록으로 돌아간다 */
    data object Closed : PostDetailEvent

    /** 신고 접수 안내 */
    data object Reported : PostDetailEvent

    data class ActionFailed(val error: UiError) : PostDetailEvent
}
