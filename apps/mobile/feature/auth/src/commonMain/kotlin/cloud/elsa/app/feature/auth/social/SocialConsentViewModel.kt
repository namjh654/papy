package cloud.elsa.app.feature.auth.social

import cloud.elsa.app.core.common.error.toAppError
import cloud.elsa.app.core.common.viewmodel.StatefulViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 소셜 신규 사용자(LoginEvent.SocialSignedUp)의 약관 동의 단계(18-4). 화면은 약관 화면(TermsViewModel)을 그대로 쓰고,
 * 그 화면의 "동의하고 계속"([agree])·뒤로([cancel])를 이 ViewModel 에 넘긴다.
 */
class SocialConsentViewModel(
    private val repository: SocialConsentRepository,
) : StatefulViewModel<SocialConsentUiState, SocialConsentEvent>(SocialConsentUiState()) {

    val uiState: StateFlow<SocialConsentUiState> = state
    val events: Flow<SocialConsentEvent> = eventFlow

    fun agree(marketing: Boolean) = launch {
        if (currentState.isLoading) return@launch
        setState { copy(isLoading = true, error = null) }
        repository.agree(marketing)
        setState { copy(isLoading = false) }
        sendEvent(SocialConsentEvent.Completed)
    }

    fun cancel() = launch {
        repository.cancel()
        sendEvent(SocialConsentEvent.Cancelled)
    }

    override fun onError(throwable: Throwable) = setState {
        copy(isLoading = false, error = throwable.toAppError().toUiError())
    }
}
