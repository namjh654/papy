package cloud.elsa.app.feature.community.blocked

import cloud.elsa.app.core.common.error.UiError
import cloud.elsa.app.core.common.error.toAppError
import cloud.elsa.app.core.common.viewmodel.StatefulViewModel
import cloud.elsa.app.core.common.viewmodel.UiEvent
import cloud.elsa.app.core.common.viewmodel.UiState
import cloud.elsa.app.feature.community.Author
import cloud.elsa.app.feature.community.CommunityRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class BlockedUsersUiState(
    val users: List<Author> = emptyList(),
    val isLoading: Boolean = true,
    val error: UiError? = null,
) : UiState

sealed interface BlockedUsersEvent : UiEvent {
    data class Unblocked(val user: Author) : BlockedUsersEvent
}

/** 설정 → 차단한 사용자: 목록과 차단 해제(App Store 1.2). Swift: `IOSAppGraph.shared.blockedUsersViewModel()` */
class BlockedUsersViewModel(
    private val repository: CommunityRepository,
) : StatefulViewModel<BlockedUsersUiState, BlockedUsersEvent>(BlockedUsersUiState()) {

    val uiState: StateFlow<BlockedUsersUiState> = state
    val events: Flow<BlockedUsersEvent> = eventFlow

    init {
        load()
    }

    fun load() = launch {
        setState { copy(isLoading = true, error = null) }
        val users = repository.blocked()
        setState { copy(users = users, isLoading = false) }
    }

    fun unblock(user: Author) = launch {
        repository.unblock(user.id)
        setState { copy(users = users.filterNot { it.id == user.id }) }
        sendEvent(BlockedUsersEvent.Unblocked(user))
    }

    override fun onError(throwable: Throwable) = setState {
        copy(isLoading = false, error = throwable.toAppError().toUiError())
    }
}
