package io.github.namjh654.papy.core.common.paging

import io.github.namjh654.papy.core.common.error.UiError

/**
 * 한 페이지 응답. [nextKey] 가 null 이면 마지막 페이지다.
 * 페이지 번호·커서 어느 쪽이든 문자열로 담는다 (페이지 번호면 "2", 커서면 서버가 준 값).
 */
data class Page<T>(
    val items: List<T>,
    val nextKey: String? = null,
)

/**
 * 목록 화면 공통 상태. 목록 화면의 UiState 는 이 값을 한 필드로 가진다:
 * `data class NoticeUiState(val list: ListState<NoticeItem> = ListState()) : UiState`
 *
 * Swift: `ListState<NoticeItem>` — `state.list.items`, `state.list.isRefreshing` …
 */
data class ListState<T>(
    val items: List<T> = emptyList(),
    /** 첫 페이지 로딩(목록이 비어 있을 때 전체 로딩 표시) */
    val isLoading: Boolean = false,
    /** 당겨서 새로고침 중(기존 목록은 그대로 보인다) */
    val isRefreshing: Boolean = false,
    /** 다음 페이지 로딩 중(목록 끝에 표시) */
    val isLoadingMore: Boolean = false,
    /** 마지막 페이지까지 받았다 */
    val endReached: Boolean = false,
    /** 첫 로딩·새로고침 실패 */
    val error: UiError? = null,
    /** 다음 페이지 실패(목록 끝에 다시 시도) */
    val loadMoreError: UiError? = null,
) {
    /** 로딩·오류 없이 비어 있다 → 빈 화면 */
    val isEmpty: Boolean
        get() = items.isEmpty() && !isLoading && !isRefreshing && error == null

    /** 보여줄 목록이 없고 오류가 있다 → 전체 오류 화면(다시 시도). 목록이 있으면 목록 위에 짧게 알린다 */
    val showFullError: Boolean
        get() = items.isEmpty() && error != null && !isLoading && !isRefreshing

    /** 목록 끝에 닿았을 때 다음 페이지를 요청해도 되는지 */
    val canLoadMore: Boolean
        get() = !endReached && !isLoading && !isRefreshing && !isLoadingMore && loadMoreError == null && error == null
}
