package cloud.elsa.app.feature.notice

import cloud.elsa.app.core.common.paging.Paginator
import cloud.elsa.app.core.common.viewmodel.StatefulViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 목록 화면 기준 예시: 진입 시 첫 페이지, 당겨서 새로고침, 끝에 닿으면 다음 페이지.
 * 오류는 [Paginator] 가 ListState 에 담으므로 onError 를 두지 않는다.
 */
class NoticeViewModel(
    private val repository: NoticeRepository,
) : StatefulViewModel<NoticeUiState, NoticeEvent>(NoticeUiState()) {

    val uiState: StateFlow<NoticeUiState> = state

    val events: Flow<NoticeEvent> = eventFlow

    private val paginator = Paginator { page -> repository.items(page) }

    init {
        launch { paginator.state.collect { list -> setState { copy(list = list) } } }
        load()
    }

    /** 첫 페이지(전체 오류 화면의 다시 시도도 이것) */
    fun load() = launch { paginator.load() }

    fun refresh() = launch { paginator.refresh() }

    /** 목록 끝에 닿았을 때 */
    fun loadMore() = launch { paginator.loadMore() }

    /** 다음 페이지 실패 후 다시 시도 */
    fun retryLoadMore() = launch { paginator.retryLoadMore() }

    fun itemClicked(item: NoticeItem) = launch {
        sendEvent(NoticeEvent.ItemSelected(item.id))
    }
}
