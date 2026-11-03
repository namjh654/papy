package io.github.namjh654.papy.feature.search

import androidx.lifecycle.viewModelScope
import io.github.namjh654.papy.core.common.paging.Paginator
import io.github.namjh654.papy.core.common.viewmodel.StatefulViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 검색 기준 예시:
 * - **입력 중 자동 검색**: 입력이 [DEBOUNCE_MILLIS] 동안 멈추면 검색한다(글자마다 요청하지 않는다)
 * - **늦게 온 결과 버리기**: 검색어가 바뀌면 [Paginator.restart] 가 이전 요청의 결과를 버린다
 * - **최근 검색어**: 키보드의 검색 버튼·최근 검색어를 눌러 확정한 검색만 저장한다(입력 중인 글자는 저장하지 않는다)
 */
class SearchViewModel(
    private val repository: SearchRepository,
) : StatefulViewModel<SearchUiState, SearchEvent>(SearchUiState()) {

    val uiState: StateFlow<SearchUiState> = state

    val events: Flow<SearchEvent> = eventFlow

    private val paginator = Paginator { page -> repository.search(currentState.searched.orEmpty(), page) }

    /** 입력이 멈추기를 기다리는 작업. 새 입력이 오면 취소한다 */
    private var debounce: Job? = null

    init {
        launch { paginator.state.collect { list -> setState { copy(results = list) } } }
        launch {
            val recent = repository.recent()
            setState { copy(recent = recent) }
        }
    }

    fun queryChanged(value: String) {
        setState { copy(query = value) }
        debounce?.cancel()
        if (value.isBlank()) {
            // 지우면 바로 최근 검색어 화면으로
            paginator.clear()
            setState { copy(searched = null) }
            return
        }
        debounce = viewModelScope.launch {
            delay(DEBOUNCE_MILLIS)
            search(value, remember = false)
        }
    }

    /** 키보드의 검색 버튼 */
    fun submit() {
        debounce?.cancel()
        val query = currentState.query
        if (query.isNotBlank()) launch { search(query, remember = true) }
    }

    fun recentClicked(query: String) {
        debounce?.cancel()
        setState { copy(query = query) }
        launch { search(query, remember = true) }
    }

    fun removeRecent(query: String) = launch {
        setState { copy(recent = recent - query) }
        repository.removeRecent(query)
    }

    fun clearRecent() = launch {
        setState { copy(recent = emptyList()) }
        repository.clearRecent()
    }

    /** 전체 오류 화면의 다시 시도 */
    fun retry() = launch { paginator.restart() }

    fun loadMore() = launch { paginator.loadMore() }

    fun retryLoadMore() = launch { paginator.retryLoadMore() }

    fun itemClicked(item: SearchResult) = launch {
        // 결과를 눌렀으면 그 검색어는 쓸모 있었던 것 → 최근 검색어에 남긴다
        currentState.searched?.let { remember(it) }
        sendEvent(SearchEvent.ItemSelected(item.id))
    }

    private suspend fun search(query: String, remember: Boolean) {
        val trimmed = query.trim()
        if (remember) remember(trimmed)
        if (trimmed == currentState.searched && currentState.results.error == null) return // 같은 검색어를 다시 요청하지 않는다
        setState { copy(searched = trimmed) }
        paginator.restart()
    }

    private suspend fun remember(query: String) {
        val recent = repository.addRecent(query)
        setState { copy(recent = recent) }
    }

    companion object {
        const val DEBOUNCE_MILLIS = 400L
    }
}
