package io.github.namjh654.papy.feature.home

import io.github.namjh654.papy.core.common.viewmodel.UiState
import io.github.namjh654.papy.core.data.session.UserModel

data class HomeUiState(
    val user: UserModel? = null,
) : UiState {
    val displayName: String
        get() = user?.name.orEmpty()
}
