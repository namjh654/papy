package io.github.namjh654.papy.feature.notice

import io.github.namjh654.papy.core.common.paging.ListState
import io.github.namjh654.papy.core.common.viewmodel.UiState

/** 목록 화면: 목록 상태(로딩·새로고침·다음 페이지·오류·빈 화면)는 [ListState] 하나로 둔다 */
data class NoticeUiState(
    val list: ListState<NoticeItem> = ListState(),
) : UiState
