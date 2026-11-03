package cloud.elsa.app.feature.community

import cloud.elsa.app.core.common.viewmodel.UiEvent

/** 화면 이동은 플랫폼 Navigation 이 결정한다. ViewModel 은 무슨 일이 있었는지만 알린다. */
sealed interface CommunityEvent : UiEvent {
    data class PostSelected(val post: Post) : CommunityEvent

    /** 글쓰기 */
    data object ComposeRequested : CommunityEvent

    /** 좋아요 실패 — 되돌렸다는 안내 */
    data object LikeFailed : CommunityEvent

    /** 맨 위로(새 글을 올린 뒤) */
    data object ScrollToTop : CommunityEvent
}
