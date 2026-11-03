package cloud.elsa.app.feature.auth.signup

import cloud.elsa.app.core.common.error.ErrorCode
import cloud.elsa.app.core.common.error.UiError
import cloud.elsa.app.core.common.viewmodel.UiState

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
