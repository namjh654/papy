package cloud.elsa.app.feature.example

import cloud.elsa.app.core.common.paging.Paginator
import cloud.elsa.app.core.common.viewmodel.StatefulViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 목록 화면 기준 예시: 진입 시 첫 페이지, 당겨서 새로고침, 끝에 닿으면 다음 페이지.
 * 오류는 [Paginator] 가 ListState 에 담으므로 onError 를 두지 않는다.
 * 상세 화면에서 수정한 항목은 [ExampleRepository.changes] 로 받아 그 항목만 바꾼다(다시 불러오지 않는다).
 */
class ExampleViewModel(
    private val repository: ExampleRepository,
) : StatefulViewModel<ExampleUiState, ExampleEvent>(ExampleUiState()) {

    val uiState: StateFlow<ExampleUiState> = state

    val events: Flow<ExampleEvent> = eventFlow

    private val paginator = Paginator { page -> repository.items(page) }

    init {
        launch { paginator.state.collect { list -> setState { copy(list = list) } } }
        launch {
            repository.changes.collect { changed ->
                paginator.update { items -> items.map { if (it.id == changed.id) changed else it } }
            }
        }
        load()
    }

    /** 첫 페이지(전체 오류 화면의 다시 시도도 이것) */
    fun load() = launch { paginator.load() }

    fun refresh() = launch { paginator.refresh() }

    /** 목록 끝에 닿았을 때 */
    fun loadMore() = launch { paginator.loadMore() }

    /** 다음 페이지 실패 후 다시 시도 */
    fun retryLoadMore() = launch { paginator.retryLoadMore() }

    fun itemClicked(item: ExampleItem) = launch {
        sendEvent(ExampleEvent.ItemSelected(item.id))
    }
}
