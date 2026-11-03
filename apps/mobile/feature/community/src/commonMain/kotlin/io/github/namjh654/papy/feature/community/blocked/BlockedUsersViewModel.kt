package io.github.namjh654.papy.feature.community.blocked

import io.github.namjh654.papy.core.common.error.UiError
import io.github.namjh654.papy.core.common.error.toAppError
import io.github.namjh654.papy.core.common.viewmodel.StatefulViewModel
import io.github.namjh654.papy.core.common.viewmodel.UiEvent
import io.github.namjh654.papy.core.common.viewmodel.UiState
import io.github.namjh654.papy.feature.community.Author
import io.github.namjh654.papy.feature.community.CommunityRepository
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
