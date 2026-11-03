package io.github.namjh654.papy.feature.auth.social

import io.github.namjh654.papy.core.common.error.UiError
import io.github.namjh654.papy.core.common.viewmodel.UiState

data class SocialConsentUiState(
    val isLoading: Boolean = false,
    val error: UiError? = null,
) : UiState
