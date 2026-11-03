package cloud.elsa.app.feature.auth.social

import cloud.elsa.app.core.common.error.UiError
import cloud.elsa.app.core.common.viewmodel.UiState

data class SocialConsentUiState(
    val isLoading: Boolean = false,
    val error: UiError? = null,
) : UiState
