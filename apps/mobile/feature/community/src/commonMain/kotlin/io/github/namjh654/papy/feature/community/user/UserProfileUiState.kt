package io.github.namjh654.papy.feature.community.user

import io.github.namjh654.papy.core.common.error.UiError
import io.github.namjh654.papy.core.common.paging.ListState
import io.github.namjh654.papy.core.common.viewmodel.UiState
import io.github.namjh654.papy.feature.community.Post
import io.github.namjh654.papy.feature.community.UserProfile

/**
 * 사용자 프로필(25-1): 위에 프로필(사진·이름·소개·글 수·메시지 보내기), 아래에 그 사람이 쓴 글([posts]).
 * [error]: 프로필을 불러오지 못했다(전체 오류 화면). 글 목록 오류는 [posts] 안에 있다.
 */
data class UserProfileUiState(
    val profile: UserProfile? = null,
    val posts: ListState<Post> = ListState(),
    val isLoading: Boolean = true,
    val error: UiError? = null,
) : UiState {
    /** 내 프로필이 아니면 메시지 보내기·차단 */
    val canInteract: Boolean get() = profile?.isMe == false
}
