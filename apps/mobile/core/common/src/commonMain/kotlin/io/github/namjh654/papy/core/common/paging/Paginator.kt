package io.github.namjh654.papy.core.common.paging

import io.github.namjh654.papy.core.common.error.toAppError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 라이브러리 없는 페이징. ViewModel 이 하나 들고 [state] 를 UiState 에 옮긴다.
 *
 * ```
 * private val paginator = Paginator { key -> repository.items(page = key) }
 * init { launch { paginator.state.collect { list -> setState { copy(list = list) } } }; load() }
 * fun load() = launch { paginator.load() }
 * fun refresh() = launch { paginator.refresh() }
 * fun loadMore() = launch { paginator.loadMore() }
 * ```
 *
 * - 오류는 던지지 않고 [ListState.error] / [ListState.loadMoreError] 에 담는다(ViewModel onError 불필요)
 * - 같은 종류의 요청이 진행 중이면 무시한다. 새로고침이 시작되면 진행 중이던 다음 페이지 결과는 버린다
 * - ViewModel(메인 스레드)에서만 호출한다
 * - 로컬 DB 에 첫 페이지를 두면([cache], DEC-130) 화면 진입 때 그것을 먼저 보여 주고 새로고침처럼 받는다.
 *   받지 못하면(오프라인) 저장된 목록을 그대로 두고 위에 오류를 짧게 알린다
 *
 * @param cache 저장해 둔 첫 페이지(없으면 빈 목록). `Paginator(cache = repository::cachedItems) { key -> … }`
 * @param load key 가 null 이면 첫 페이지
 */
class Paginator<T>(
    private val cache: (suspend () -> List<T>)? = null,
    private val load: suspend (key: String?) -> Page<T>,
) {
    private val _state = MutableStateFlow(ListState<T>())
    val state: StateFlow<ListState<T>> = _state.asStateFlow()

    private var nextKey: String? = null

    /** 새로고침마다 올라간다. 요청이 끝났을 때 값이 달라졌으면 그 결과는 버린다 */
    private var generation = 0

    /** 첫 페이지(화면 진입, 전체 오류 화면의 다시 시도). 이미 목록이 있으면 아무것도 하지 않는다 */
    suspend fun load() {
        val current = _state.value
        if (current.isLoading || current.isRefreshing || current.items.isNotEmpty()) return
        _state.update { it.copy(isLoading = true, error = null, loadMoreError = null) }
        val cached = readCache()
        if (cached.isNotEmpty() && _state.value.items.isEmpty()) _state.value = ListState(items = cached, isRefreshing = true)
        loadFirstPage()
    }

    /** 저장된 첫 페이지. 읽지 못하면 없는 것으로(서버에서 받는다) */
    private suspend fun readCache(): List<T> {
        val cache = cache ?: return emptyList()
        return try {
            cache()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            emptyList()
        }
    }

    /** 당겨서 새로고침: 목록을 유지한 채 첫 페이지를 다시 받는다 */
    suspend fun refresh() {
        val current = _state.value
        if (current.isLoading || current.isRefreshing) return
        _state.update { it.copy(isRefreshing = true, isLoadingMore = false, error = null, loadMoreError = null) }
        loadFirstPage()
    }

    /**
     * 조건(검색어·필터)이 바뀌어 처음부터 다시 받는다: 목록을 비우고 첫 페이지를 불러온다.
     * 진행 중이던 이전 조건의 결과는 버린다. (같은 조건으로 다시 받는 것은 [refresh])
     */
    suspend fun restart() {
        _state.value = ListState(isLoading = true)
        loadFirstPage()
    }

    /** 목록을 비운다(검색어를 지웠을 때 등). 진행 중이던 결과는 버린다 */
    fun clear() {
        generation++
        nextKey = null
        _state.value = ListState()
    }

    /** 목록 끝에 닿았을 때 */
    suspend fun loadMore() {
        if (!_state.value.canLoadMore) return
        _state.update { it.copy(isLoadingMore = true) }
        val requested = generation
        try {
            val page = load(nextKey)
            if (requested != generation) return
            nextKey = page.nextKey
            _state.update {
                it.copy(items = it.items + page.items, isLoadingMore = false, endReached = page.nextKey == null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (requested != generation) return
            _state.update { it.copy(isLoadingMore = false, loadMoreError = e.toAppError().toUiError()) }
        }
    }

    /** 다음 페이지 실패 후 목록 끝의 다시 시도 */
    suspend fun retryLoadMore() {
        _state.update { it.copy(loadMoreError = null) }
        loadMore()
    }

    /**
     * 이미 받은 목록의 항목을 바꾼다(다시 불러오지 않는다). 상세 화면에서 수정·삭제한 결과를 목록에 반영할 때 쓴다:
     * `paginator.update { items -> items.map { if (it.id == changed.id) changed else it } }`
     */
    fun update(transform: (List<T>) -> List<T>) {
        _state.update { it.copy(items = transform(it.items)) }
    }

    private suspend fun loadFirstPage() {
        val requested = ++generation
        try {
            val page = load(null)
            if (requested != generation) return
            nextKey = page.nextKey
            _state.value = ListState(items = page.items, endReached = page.nextKey == null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (requested != generation) return
            _state.update { it.copy(isLoading = false, isRefreshing = false, error = e.toAppError().toUiError()) }
        }
    }
}
