package cloud.elsa.app.core.data.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AuthStatus {
    /** 세션 복원 전 */
    Unknown,
    Authenticated,
    Unauthenticated,
}

/** 앱 전역 인증 상태의 단일 소스입니다. 갱신은 SessionRepository만 수행합니다. */
class AuthStore {

    private val _status = MutableStateFlow(AuthStatus.Unknown)
    val status: StateFlow<AuthStatus> = _status.asStateFlow()

    val isLoggedIn: Boolean
        get() = _status.value == AuthStatus.Authenticated

    fun authenticated() {
        _status.value = AuthStatus.Authenticated
    }

    fun unauthenticated() {
        _status.value = AuthStatus.Unauthenticated
    }
}
