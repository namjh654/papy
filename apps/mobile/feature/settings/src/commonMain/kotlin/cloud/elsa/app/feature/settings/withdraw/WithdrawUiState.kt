package cloud.elsa.app.feature.settings.withdraw

import cloud.elsa.app.core.common.viewmodel.UiState
import cloud.elsa.app.core.common.error.UiError

/**
 * @property confirmed "안내를 확인했어요" 체크
 */
data class WithdrawUiState(
    val password: String = "",
    val confirmed: Boolean = false,
    val isLoading: Boolean = false,
    val error: UiError? = null,
    /** 소셜로 가입한 사용자는 비밀번호가 없다 → 비밀번호 칸 없이 확인만(18-4) */
    val requiresPassword: Boolean = true,
) : UiState {
    val canSubmit: Boolean
        get() = (!requiresPassword || password.isNotBlank()) && confirmed && !isLoading
}
