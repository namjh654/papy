package io.github.namjh654.papy.feature.chat

import io.github.namjh654.papy.core.common.paging.Paginator
import io.github.namjh654.papy.core.common.viewmodel.StatefulViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 대화 목록: 진입 시 첫 페이지(저장된 목록이 있으면 먼저 보인다), 당겨서 새로고침, 끝에 닿으면 다음 페이지.
 * 오류는 [Paginator] 가 ListState 에 담으므로 onError 를 두지 않는다.
 */
class ChatViewModel(
    private val repository: ChatRepository,
) : StatefulViewModel<ChatUiState, ChatEvent>(ChatUiState()) {

    val uiState: StateFlow<ChatUiState> = state

    val events: Flow<ChatEvent> = eventFlow

    // 저장된 첫 페이지를 먼저 보여 주고 받는다(DEC-130)
    private val paginator = Paginator(cache = repository::cachedConversations) { page -> repository.conversations(page) }

    init {
        launch { paginator.state.collect { list -> setState { copy(list = list) } } }
        // 방을 읽고 돌아오면 그 줄의 안 읽은 수를 지운다(다시 불러오지 않고)
        launch {
            repository.unread.collect { unread ->
                paginator.update { items -> items.map { c -> unread[c.id]?.let { c.copy(unreadCount = it) } ?: c } }
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

    fun conversationClicked(conversation: Conversation) = launch {
        sendEvent(ChatEvent.ConversationSelected(conversation))
    }
}
