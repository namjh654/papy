package io.github.namjh654.papy.feature.inbox

import io.github.namjh654.papy.core.common.paging.Paginator
import io.github.namjh654.papy.core.common.viewmodel.StatefulViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 알림함: 받은 알림 목록, 누르면 읽음 처리 + 연결된 화면 열기, 모두 읽음.
 *
 * 읽음 처리는 **먼저 화면에 반영하고** 서버에 보낸다(낙관적 갱신). 서버 요청이 실패해도 되돌리지 않는다 —
 * 다음에 목록을 받을 때 서버 값으로 맞춰진다(읽음 표시는 틀려도 큰 문제가 없는 값이라 이렇게 한다).
 */
class InboxViewModel(
    private val repository: InboxRepository,
) : StatefulViewModel<InboxUiState, InboxEvent>(InboxUiState()) {

    val uiState: StateFlow<InboxUiState> = state

    val events: Flow<InboxEvent> = eventFlow

    private val paginator = Paginator { page -> repository.items(page) }

    init {
        launch { paginator.state.collect { list -> setState { copy(list = list) } } }
        load()
    }

    fun load() = launch { paginator.load() }

    fun refresh() = launch { paginator.refresh() }

    fun loadMore() = launch { paginator.loadMore() }

    fun retryLoadMore() = launch { paginator.retryLoadMore() }

    fun itemClicked(item: InboxItem) {
        if (!item.read) {
            paginator.update { items -> items.map { if (it.id == item.id) it.copy(read = true) else it } }
            launchQuietly { repository.markRead(item.id) }
        }
        item.link?.let { link -> launch { sendEvent(InboxEvent.Open(link)) } }
    }

    fun markAllRead() {
        if (!currentState.hasUnread) return
        paginator.update { items -> items.map { it.copy(read = true) } }
        launchQuietly { repository.markAllRead() }
    }

    /** 실패해도 화면에 알리지 않는 요청 */
    private fun launchQuietly(block: suspend () -> Unit) = launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // 다음 목록 요청 때 서버 값으로 맞춰진다
        }
    }
}
