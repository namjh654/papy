package io.github.namjh654.papy.feature.community.user

import io.github.namjh654.papy.core.common.error.UiError
import io.github.namjh654.papy.core.common.viewmodel.UiEvent
import io.github.namjh654.papy.feature.community.Post

sealed interface UserProfileEvent : UiEvent {
    data class PostSelected(val post: Post) : UserProfileEvent

    /** 메시지 보내기 → 플랫폼이 그 사람과의 대화방을 연다(채팅 모듈이 대화를 찾거나 만든다, 25-2) */
    data class MessageRequested(val userId: String, val name: String) : UserProfileEvent

    /** 차단했다 — 프로필을 닫는다 */
    data object Blocked : UserProfileEvent

    data class ActionFailed(val error: UiError) : UserProfileEvent
}
