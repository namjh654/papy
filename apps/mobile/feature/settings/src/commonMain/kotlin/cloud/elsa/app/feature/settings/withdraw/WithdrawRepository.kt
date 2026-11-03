package cloud.elsa.app.feature.settings.withdraw

import cloud.elsa.app.core.common.error.AppError
import cloud.elsa.app.core.common.error.ErrorCode
import cloud.elsa.app.core.data.session.SessionRepository
import cloud.elsa.app.core.data.session.UserStore
import cloud.elsa.app.core.data.social.SocialAuthProvider
import cloud.elsa.app.core.data.storage.login.LoginStorage

/**
 * 회원 탈퇴: 서버에서 계정을 지운 뒤 이 기기의 흔적(세션·저장한 아이디·자동 로그인)을 모두 지운다.
 * 서버 요청이 실패하면 아무것도 지우지 않는다. 소셜로 가입한 사용자는 비밀번호 없이 탈퇴하고, 제공자와의 앱 연결도 끊는다(18-4).
 */
class WithdrawRepository(
    private val api: WithdrawApi,
    private val sessionRepository: SessionRepository,
    private val loginStorage: LoginStorage,
    private val userStore: UserStore,
    private val socialAuth: SocialAuthProvider,
) {
    /** @param password 아이디 사용자는 필수, 소셜 사용자는 null */
    suspend fun withdraw(password: String?) {
        val provider = userStore.user.value?.provider
        if (provider == null && password.isNullOrBlank()) throw AppError.Validation(ErrorCode.PasswordRequired)
        api.withdraw(WithdrawRequest(password = password.takeIf { provider == null }))
        // 소셜 사용자: 서버 탈퇴가 끝난 뒤(SDK 토큰이 남아 있을 때) 제공자 연결을 끊는다
        provider?.let(socialAuth::unlink)
        // 저장한 아이디를 먼저 지운다 — logout() 이 인증 상태를 바꾸면 이 코루틴은 취소될 수 있다
        loginStorage.clear()
        sessionRepository.logout()
    }
}
