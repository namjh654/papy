package cloud.elsa.app.feature.home

import cloud.elsa.app.core.common.viewmodel.UiState
import cloud.elsa.app.core.data.session.UserModel

data class HomeUiState(
    val user: UserModel? = null,
) : UiState {
    val displayName: String
        get() = user?.name.orEmpty()
}
