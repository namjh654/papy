package cloud.elsa.app.core.data.session

import kotlinx.coroutines.CancellationException
import cloud.elsa.app.core.data.storage.login.LoginStorage
import cloud.elsa.app.core.data.storage.token.TokenStorage
import cloud.elsa.app.core.data.storage.user.UserStorage

data class AuthSession(
    val accessToken: String,
    val refreshToken: String?,
    val user: UserModel,
)

/**
 * 인증 세션의 시작/복원/종료를 담당합니다.
 *
 * Token·User 영속화(Storage)와 메모리 상태(Store)를 항상 함께 갱신해
 * 두 상태가 어긋나지 않도록 합니다.
 */
class SessionRepository(
    private val tokenStorage: TokenStorage,
    private val userStorage: UserStorage,
    private val loginStorage: LoginStorage,
    private val authStore: AuthStore,
    private val userStore: UserStore,
    private val listeners: List<SessionListener> = emptyList(),
) {

    suspend fun start(session: AuthSession) {
        tokenStorage.setAccessToken(session.accessToken)
        session.refreshToken?.let { tokenStorage.setRefreshToken(it) }
        userStorage.setUser(session.user)

        userStore.setUser(session.user)
        authStore.authenticated()
        notify { onSessionStarted() }
    }

    /** 프로필 수정 뒤: 저장소와 메모리([UserStore])를 함께 바꾼다 → 사용자를 보여주는 화면이 모두 따라온다 */
    suspend fun updateUser(user: UserModel) {
        userStorage.setUser(user)
        userStore.setUser(user)
    }

    /**
     * 자동 로그인이 켜져 있고 토큰·사용자가 모두 남아 있으면 세션을 복원합니다.
     * 그 외에는 잔여 인증 정보를 지우고 비로그인 상태로 둡니다.
     *
     * @return 복원된 사용자, 복원하지 못하면 null
     */
    suspend fun restore(): UserModel? {
        val token = tokenStorage.getAccessToken()
        val user = userStorage.getUser()

        if (loginStorage.isAutoLogin() && token != null && user != null) {
            userStore.setUser(user)
            authStore.authenticated()
            notify { onSessionStarted() }
            return user
        }

        clearCredentials()
        return null
    }

    /**
     * 서버가 토큰을 거부했을 때(401) BaseApi 가 호출한다.
     * 토큰·사용자만 지우고 로그인 옵션(아이디 저장·자동 로그인)은 유지한다 → AuthStore 가 Unauthenticated 가 되어
     * 양 플랫폼 루트가 로그인 화면으로 전환한다.
     */
    suspend fun expire() {
        clearCredentials()
    }

    /**
     * 순서가 중요하다: 인증 상태를 바꾸면([clearCredentials] 의 마지막 줄) 화면이 로그인으로 넘어가면서
     * 이 함수를 부른 화면의 코루틴이 취소될 수 있다. 저장소 정리는 그 전에 모두 끝낸다.
     */
    suspend fun logout() {
        notify { onSessionEnding() } // 토큰이 남아 있을 때(서버 해제 가능)
        loginStorage.setAutoLogin(false)
        clearCredentials()
    }

    private suspend fun notify(event: suspend SessionListener.() -> Unit) {
        listeners.forEach { listener ->
            try {
                listener.event()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // 리스너 실패가 로그인·로그아웃을 막지 않게
            }
        }
    }

    private suspend fun clearCredentials() {
        tokenStorage.clear()
        userStorage.clear()
        userStore.clear()
        authStore.unauthenticated() // 항상 마지막 — 이 뒤의 코드는 실행되지 않을 수 있다
    }
}
