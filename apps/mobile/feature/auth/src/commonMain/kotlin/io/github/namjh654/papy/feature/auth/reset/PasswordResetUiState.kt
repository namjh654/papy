package io.github.namjh654.papy.feature.auth.reset

import io.github.namjh654.papy.core.common.error.UiError
import io.github.namjh654.papy.core.common.viewmodel.UiState

/** 한 화면 안의 두 단계 */
enum class PasswordResetStep {
    /** 아이디 입력 → 인증 코드 요청 */
    Request,

    /** 인증 코드 + 새 비밀번호 입력 */
    Confirm,
}

data class PasswordResetUiState(
    val step: PasswordResetStep = PasswordResetStep.Request,
    val username: String = "",
    val code: String = "",
    val password: String = "",
    val passwordConfirmation: String = "",
    val isLoading: Boolean = false,
    val error: UiError? = null,
) : UiState {
    val canRequest: Boolean
        get() = username.isNotBlank() && !isLoading

    val canConfirm: Boolean
        get() = code.isNotBlank() && password.isNotBlank() && passwordConfirmation.isNotBlank() && !isLoading

    /** 둘째 단계에서는 뒤로가기가 첫 단계로 간다 → 화면이 시스템 뒤로가기를 가로챈다 */
    val canGoToPreviousStep: Boolean
        get() = step == PasswordResetStep.Confirm
}
