package io.github.namjh654.papy.feature.search

import io.github.namjh654.papy.core.common.viewmodel.UiState
import io.github.namjh654.papy.core.common.paging.ListState

/**
 * @property query 입력칸의 글자
 * @property searched 결과 목록이 가리키는 검색어. null 이면 아직 검색 전(최근 검색어를 보여준다)
 */
data class SearchUiState(
    val query: String = "",
    val searched: String? = null,
    val results: ListState<SearchResult> = ListState(),
    val recent: List<String> = emptyList(),
) : UiState {
    /** 검색 전: 최근 검색어 화면 */
    val showRecent: Boolean
        get() = searched == null
}
