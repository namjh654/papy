package io.github.namjh654.papy.feature.auth.login

import io.github.namjh654.papy.core.common.error.ErrorCode
import io.github.namjh654.papy.core.common.error.UiError
import io.github.namjh654.papy.core.common.error.toAppError
import io.github.namjh654.papy.core.data.social.SocialConfig
import io.github.namjh654.papy.core.data.social.SocialCredential
import io.github.namjh654.papy.core.data.social.SocialProvider
import io.github.namjh654.papy.feature.auth.social.SocialLoginRepository
import io.github.namjh654.papy.core.common.viewmodel.StatefulViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull

class LoginViewModel(
    private val loginRepository: LoginRepository,
    private val loginNotices: LoginNoticeStore,
    private val socialLoginRepository: SocialLoginRepository,
    socialProviders: List<SocialProvider> = SocialConfig.providers,
    /** dev 전용: SDK 없이 가짜 인증 값으로 서버(목업)와 교환한다(DEC-108) */
    private val socialMock: Boolean = SocialConfig.isMock,
) : StatefulViewModel<LoginUiState, LoginEvent>(LoginUiState(socialProviders = socialProviders)) {

    val uiState: StateFlow<LoginUiState> = state

    /** 로그인 성공 등 일회성 이벤트. Native UI가 구독해 화면 이동을 처리합니다. */
    val events: Flow<LoginEvent> = eventFlow

    init {
        restoreLoginState()
        // 회원가입·비밀번호 재설정이 남긴 안내를 한 번만 전달한다
        launch {
            loginNotices.pending.filterNotNull().collect { notice ->
                loginNotices.consume()
                sendEvent(LoginEvent.Notice(notice))
            }
        }
    }

    // ============================================================
    // Input
    // ============================================================

    fun usernameChanged(value: String) = setState {
        copy(username = value, error = null, isLoginEnabled = value.isNotBlank() && password.isNotBlank())
    }

    fun passwordChanged(value: String) = setState {
        copy(password = value, error = null, isLoginEnabled = username.isNotBlank() && value.isNotBlank())
    }

    fun rememberIdChanged(value: Boolean) = setState { copy(rememberId = value) }

    fun autoLoginChanged(value: Boolean) = setState { copy(autoLogin = value) }

    // ============================================================
    // Action
    // ============================================================

    fun login() = launch {
        val state = currentState
        if (state.isLoading) return@launch // 중복 로그인 방지

        setState { copy(isLoading = true, error = null) }

        val user = loginRepository.login(
            username = state.username,
            password = state.password,
            rememberId = state.rememberId,
            autoLogin = state.autoLogin,
        )

        setState { copy(isLoading = false, password = "", isLoginEnabled = false) }
        sendEvent(LoginEvent.LoginSucceeded(user))
    }

    // ============================================================
    // 소셜 로그인 (DEC-105~109)
    // ============================================================

    /** 소셜 버튼. 보통은 플랫폼에 SDK 인증을 맡기고([LoginEvent.StartSocialSignIn]), mock 이면 바로 교환한다 */
    fun socialLoginTapped(provider: SocialProvider) {
        if (currentState.isLoading) return
        if (socialMock) {
            socialCredentialReceived(SocialCredential(provider = provider, token = "mock-${provider.id}"))
            return
        }
        setState { copy(isLoading = true, error = null) }
        launch { sendEvent(LoginEvent.StartSocialSignIn(provider)) }
    }

    /** 플랫폼 SDK 인증 성공 → 서버와 교환해 세션 시작 */
    fun socialCredentialReceived(credential: SocialCredential) = launch {
        setState { copy(isLoading = true, error = null) }
        val result = socialLoginRepository.login(credential, autoLogin = currentState.autoLogin)
        setState { copy(isLoading = false) }
        sendEvent(if (result.isNewUser) LoginEvent.SocialSignedUp(result.user) else LoginEvent.LoginSucceeded(result.user))
    }

    /** 사용자가 SDK 화면을 닫았다 → 오류 없이 원래대로 */
    fun socialSignInCancelled() = setState { copy(isLoading = false) }

    /** SDK 가 실패했다(네트워크·설정 오류 등) */
    fun socialSignInFailed() = setState { copy(isLoading = false, error = UiError(ErrorCode.SocialLoginFailed)) }

    // ============================================================
    // Internal
    // ============================================================

    /** 저장된 로그인 설정 복원 */
    private fun restoreLoginState() = launch {
        val preference = loginRepository.getLoginPreference()

        setState {
            copy(
                username = preference.savedId,
                rememberId = preference.rememberId,
                autoLogin = preference.autoLogin,
                error = null,
                isLoginEnabled = preference.savedId.isNotBlank() && password.isNotBlank(),
            )
        }
    }

    /** launch 안의 모든 예외 → AppError → UiState.error. 실패하면 아이디는 두고 비밀번호만 비운다(DEC-116) */
    override fun onError(throwable: Throwable) = setState {
        copy(isLoading = false, password = "", isLoginEnabled = false, error = throwable.toAppError().toUiError())
    }
}
