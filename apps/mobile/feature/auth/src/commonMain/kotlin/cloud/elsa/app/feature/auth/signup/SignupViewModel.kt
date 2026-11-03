package cloud.elsa.app.feature.auth.signup

import cloud.elsa.app.core.common.error.ErrorCode
import cloud.elsa.app.core.common.error.UiError
import cloud.elsa.app.core.common.error.toAppError
import cloud.elsa.app.core.common.viewmodel.StatefulViewModel
import cloud.elsa.app.feature.auth.login.LoginNotice
import cloud.elsa.app.feature.auth.login.LoginNoticeStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class SignupViewModel(
    /** 약관 화면에서 넘어온 선택 동의 값(Koin `parametersOf(marketingAgreed)`) */
    private val marketingAgreed: Boolean,
    private val signupRepository: SignupRepository,
    private val loginNotices: LoginNoticeStore,
) : StatefulViewModel<SignupUiState, SignupEvent>(SignupUiState()) {

    val uiState: StateFlow<SignupUiState> = state

    /** 가입 성공 등 일회성 이벤트. Native UI가 구독해 화면 이동을 처리합니다. */
    val events: Flow<SignupEvent> = eventFlow

    // ============================================================
    // Input
    // ============================================================

    fun usernameChanged(value: String) = setState { copy(username = value, error = null) }

    fun passwordChanged(value: String) = setState { copy(password = value, error = null) }

    fun passwordConfirmationChanged(value: String) = setState { copy(passwordConfirmation = value, error = null) }

    // ============================================================
    // Action
    // ============================================================

    fun signup() = launch {
        val state = currentState
        if (state.isLoading) return@launch // 중복 요청 방지

        setState { copy(isLoading = true, error = null) }

        val result = signupRepository.signup(state.username, state.password, state.passwordConfirmation, marketingAgreed)

        if (result.success) {
            setState { copy(isLoading = false) }
            loginNotices.post(LoginNotice.SignedUp) // 로그인 화면이 돌아왔을 때 안내한다
            sendEvent(SignupEvent.SignupSucceeded(username = state.username))
        } else {
            setState { copy(isLoading = false, error = UiError(ErrorCode.SignupFailed)) }
        }
    }

    /** launch 안의 모든 예외 → AppError → UiState.error */
    override fun onError(throwable: Throwable) = setState {
        copy(isLoading = false, error = throwable.toAppError().toUiError())
    }
}
