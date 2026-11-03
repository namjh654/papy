package io.github.namjh654.papy.feature.auth.signup

import io.github.namjh654.papy.core.common.error.ErrorCode
import io.github.namjh654.papy.core.common.error.UiError
import io.github.namjh654.papy.core.common.viewmodel.UiState

data class SignupUiState(
    val username: String = "",
    val password: String = "",
    val passwordConfirmation: String = "",
    val isLoading: Boolean = false,
    /** 문구는 Native UI 가 [ErrorCode] 로 결정한다 */
    val error: UiError? = null,
) : UiState {
    val isSignupEnabled: Boolean
        get() = username.isNotBlank() &&
            password.isNotBlank() &&
            passwordConfirmation.isNotBlank() &&
            !isLoading
}
