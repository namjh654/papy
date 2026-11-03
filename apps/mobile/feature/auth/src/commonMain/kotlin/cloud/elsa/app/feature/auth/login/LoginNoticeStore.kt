package cloud.elsa.app.feature.auth.login

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 다른 화면을 마치고 로그인으로 돌아왔을 때 보여줄 안내 */
enum class LoginNotice { SignedUp, PasswordChanged }

/**
 * 이전 화면(로그인)에 결과를 알리는 기준: 같은 feature 안의 작은 저장소에 남기고, 받는 화면의 ViewModel 이 읽어 간다.
 * (화면 이동은 플랫폼 Navigation 이 하므로 KMP 가 "돌아간 화면"을 직접 부를 수 없다. 플랫폼 코드 없이 양쪽이 같게 동작한다.)
 */
class LoginNoticeStore {
    private val _pending = MutableStateFlow<LoginNotice?>(null)
    val pending: StateFlow<LoginNotice?> = _pending.asStateFlow()

    fun post(notice: LoginNotice) {
        _pending.value = notice
    }

    fun consume() {
        _pending.value = null
    }
}
