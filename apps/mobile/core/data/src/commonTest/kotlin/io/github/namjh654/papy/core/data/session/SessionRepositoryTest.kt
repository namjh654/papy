package io.github.namjh654.papy.core.data.session

import io.github.namjh654.papy.core.data.storage.FakeLocalStorage
import io.github.namjh654.papy.core.data.storage.FakeSecureStorage
import io.github.namjh654.papy.core.data.storage.login.LoginStorage
import io.github.namjh654.papy.core.data.storage.token.TokenStorage
import io.github.namjh654.papy.core.data.storage.user.UserStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class SessionRepositoryTest {

    private val storage = FakeLocalStorage()
    private val secure = FakeSecureStorage()
    private val loginStorage = LoginStorage(storage)
    private val authStore = AuthStore()
    private val userStore = UserStore()

    private fun repository() = SessionRepository(
        tokenStorage = TokenStorage(secure, storage),
        userStorage = UserStorage(storage),
        loginStorage = loginStorage,
        authStore = authStore,
        userStore = userStore,
    )

    private val user = UserModel(id = "papy", name = "Papy")

    @Test
    fun restoresPersistedSessionWhenAutoLoginEnabled() = runTest {
        loginStorage.setAutoLogin(true)
        repository().start(AuthSession("token", null, user))

        // 앱 재시작: 메모리 Store는 새로 생성되고 Storage만 남아 있음
        val restoredStore = AuthStore()
        val restored = SessionRepository(
            TokenStorage(secure, storage), UserStorage(storage), loginStorage, restoredStore, UserStore(),
        ).restore()

        assertEquals(user, restored)
        assertEquals(AuthStatus.Authenticated, restoredStore.status.value)
    }

    @Test
    fun clearsCredentialsWhenAutoLoginDisabled() = runTest {
        loginStorage.setAutoLogin(false)
        repository().start(AuthSession("token", "refresh", user))

        assertNull(repository().restore())
        assertNull(TokenStorage(secure, storage).getAccessToken())
        assertEquals(AuthStatus.Unauthenticated, authStore.status.value)
    }

    @Test
    fun expireClearsCredentialsButKeepsLoginOptions() = runTest {
        loginStorage.setAutoLogin(true)
        loginStorage.setRememberId(true)
        val repository = repository()
        repository.start(AuthSession("token", "refresh", user))

        repository.expire()

        assertNull(TokenStorage(secure, storage).getAccessToken())
        assertNull(userStore.user.value)
        assertEquals(AuthStatus.Unauthenticated, authStore.status.value)
        assertEquals(true, loginStorage.isAutoLogin())
        assertEquals(true, loginStorage.isRememberId())
    }

    @Test
    fun logoutClearsSessionAndDisablesAutoLogin() = runTest {
        loginStorage.setAutoLogin(true)
        val repository = repository()
        repository.start(AuthSession("token", null, user))

        repository.logout()

        assertNull(userStore.user.value)
        assertNull(UserStorage(storage).getUser())
        assertEquals(false, loginStorage.isAutoLogin())
        assertEquals(AuthStatus.Unauthenticated, authStore.status.value)
    }

    /** 상태가 바뀌는 순간 화면이 넘어가며 호출한 코루틴이 취소될 수 있다 → 그 전에 저장소 정리가 끝나 있어야 한다 */
    @Test
    fun logoutFinishesStorageCleanupBeforeAuthStatusChanges() = runTest {
        loginStorage.setAutoLogin(true)
        val repository = repository()
        repository.start(AuthSession("token", "refresh", user))

        var autoLoginWhenSignedOut: Boolean? = null
        var tokenWhenSignedOut: String? = "unset"
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            authStore.status.collect { status ->
                if (status == AuthStatus.Unauthenticated) {
                    autoLoginWhenSignedOut = loginStorage.isAutoLogin()
                    tokenWhenSignedOut = TokenStorage(secure, storage).getAccessToken()
                }
            }
        }

        repository.logout()

        assertEquals(false, autoLoginWhenSignedOut)
        assertNull(tokenWhenSignedOut)
    }
}
