package cloud.elsa.app.feature.auth.login

import cloud.elsa.app.core.common.error.ErrorCode
import cloud.elsa.app.core.common.error.UiError
import cloud.elsa.app.core.common.viewmodel.UiState
import cloud.elsa.app.core.data.social.SocialProvider

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val rememberId: Boolean = false,
    val autoLogin: Boolean = false,
    val isLoading: Boolean = false,
    val isLoginEnabled: Boolean = false,
    /** 문구는 Native UI 가 [ErrorCode] 로 결정한다 */
    val error: UiError? = null,
    /** 보여줄 소셜 로그인 버튼(표시 순서) — env 키가 있는 제공자만(DEC-107) */
    val socialProviders: List<SocialProvider> = emptyList(),
) : UiState
