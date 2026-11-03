package io.github.namjh654.papy.feature.community

import io.github.namjh654.papy.core.common.paging.ListState
import io.github.namjh654.papy.core.common.viewmodel.UiState

/** 커뮤니티 목록: 목록 상태(로딩·새로고침·다음 페이지·오류·빈 화면)는 [ListState] 하나로 둔다 */
data class CommunityUiState(
    val list: ListState<Post> = ListState(),
    /** 전체 · 내 글(세그먼트 탭) */
    val filter: CommunityFilter = CommunityFilter.All,
) : UiState
