package io.github.namjh654.papy.feature.auth.reset

import io.github.namjh654.papy.core.common.error.toAppError
import io.github.namjh654.papy.core.common.viewmodel.StatefulViewModel
import io.github.namjh654.papy.feature.auth.login.LoginNotice
import io.github.namjh654.papy.feature.auth.login.LoginNoticeStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 비밀번호 재설정(로그인 화면에서 진입). 한 화면에서 두 단계를 밟는다:
 * 아이디 → 인증 코드 요청 → 코드 + 새 비밀번호 → 완료되면 로그인으로 돌아간다.
 *
 * 여러 단계를 한 ViewModel 에 둘 때의 기준: 단계는 상태([PasswordResetStep])로 두고,
 * 화면은 [PasswordResetUiState.canGoToPreviousStep] 일 때 뒤로가기를 가로채 [previousStep] 을 부른다
 * (Android `BackHandler(enabled = …)`, iOS 는 뒤로 버튼을 바꿔 단다).
 */
class PasswordResetViewModel(
    private val repository: PasswordResetRepository,
    private val loginNotices: LoginNoticeStore,
) : StatefulViewModel<PasswordResetUiState, PasswordResetEvent>(PasswordResetUiState()) {

    val uiState: StateFlow<PasswordResetUiState> = state

    val events: Flow<PasswordResetEvent> = eventFlow

    fun usernameChanged(value: String) = setState { copy(username = value, error = null) }

    fun codeChanged(value: String) = setState { copy(code = value, error = null) }

    fun passwordChanged(value: String) = setState { copy(password = value, error = null) }

    fun passwordConfirmationChanged(value: String) = setState { copy(passwordConfirmation = value, error = null) }

    /** 첫 단계: 인증 코드 요청 → 둘째 단계로 */
    fun requestCode() = launch {
        if (currentState.isLoading) return@launch
        setState { copy(isLoading = true, error = null) }
        repository.requestCode(currentState.username)
        setState { copy(isLoading = false, step = PasswordResetStep.Confirm) }
    }

    /** 둘째 단계: 코드 다시 받기 */
    fun resendCode() = launch {
        if (currentState.isLoading) return@launch
        setState { copy(isLoading = true, error = null) }
        repository.requestCode(currentState.username)
        setState { copy(isLoading = false, code = "") }
        sendEvent(PasswordResetEvent.CodeResent)
    }

    fun confirm() = launch {
        val state = currentState
        if (state.isLoading) return@launch
        setState { copy(isLoading = true, error = null) }
        repository.confirm(state.username, state.code, state.password, state.passwordConfirmation)
        setState { copy(isLoading = false) }
        loginNotices.post(LoginNotice.PasswordChanged)
        sendEvent(PasswordResetEvent.Done)
    }

    /** 둘째 단계의 뒤로가기: 첫 단계로(아이디는 남긴다) */
    fun previousStep() = setState {
        copy(step = PasswordResetStep.Request, code = "", password = "", passwordConfirmation = "", error = null)
    }

    override fun onError(throwable: Throwable) = setState {
        copy(isLoading = false, error = throwable.toAppError().toUiError())
    }
}
