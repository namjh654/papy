package cloud.elsa.app.feature.settings.withdraw

import cloud.elsa.app.core.common.error.toAppError
import cloud.elsa.app.core.common.viewmodel.StatefulViewModel
import cloud.elsa.app.core.data.session.UserStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 회원 탈퇴(설정 → 계정). 되돌릴 수 없는 작업의 기준:
 * 안내 확인 체크 + 본인 확인(비밀번호) + 화면의 확인 다이얼로그를 거친 뒤에만 요청한다.
 * (계정을 만들 수 있는 앱은 앱 안에서 계정을 삭제할 수 있어야 한다 — App Store 심사 지침 5.1.1)
 */
class WithdrawViewModel(
    private val repository: WithdrawRepository,
    userStore: UserStore,
) : StatefulViewModel<WithdrawUiState, WithdrawEvent>(
    WithdrawUiState(requiresPassword = userStore.user.value?.provider == null),
) {

    val uiState: StateFlow<WithdrawUiState> = state

    val events: Flow<WithdrawEvent> = eventFlow

    fun passwordChanged(value: String) = setState { copy(password = value, error = null) }

    fun confirmedChanged(value: Boolean) = setState { copy(confirmed = value) }

    /** 화면이 확인 다이얼로그에서 "탈퇴"를 눌렀을 때 부른다 */
    fun withdraw() = launch {
        val state = currentState
        if (!state.canSubmit) return@launch // 중복 요청·미확인 방지
        setState { copy(isLoading = true, error = null) }
        repository.withdraw(password = state.password.takeIf { state.requiresPassword })
        setState { copy(isLoading = false) }
        sendEvent(WithdrawEvent.Withdrawn)
    }

    override fun onError(throwable: Throwable) = setState {
        copy(isLoading = false, error = throwable.toAppError().toUiError())
    }
}
