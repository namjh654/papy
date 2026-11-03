package cloud.elsa.app.feature.home

import cloud.elsa.app.core.common.viewmodel.StatefulViewModel
import cloud.elsa.app.core.data.session.UserStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** 홈 탭: 현재 사용자 표시 */
class HomeViewModel(
    private val userStore: UserStore,
) : StatefulViewModel<HomeUiState, HomeEvent>(HomeUiState(user = userStore.user.value)) {

    val uiState: StateFlow<HomeUiState> = state

    val events: Flow<HomeEvent> = eventFlow

    init {
        launch {
            userStore.user.collect { user -> setState { copy(user = user) } }
        }
    }
}
