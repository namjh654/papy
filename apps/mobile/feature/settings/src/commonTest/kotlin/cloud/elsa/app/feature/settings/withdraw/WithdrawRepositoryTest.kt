package cloud.elsa.app.feature.settings.withdraw

import cloud.elsa.app.core.common.error.AppError
import cloud.elsa.app.core.common.error.ErrorCode
import cloud.elsa.app.core.data.session.AuthSession
import cloud.elsa.app.core.data.session.AuthStatus
import cloud.elsa.app.core.data.session.AuthStore
import cloud.elsa.app.core.data.session.SessionRepository
import cloud.elsa.app.core.data.session.UserModel
import cloud.elsa.app.core.data.session.UserStore
import cloud.elsa.app.core.data.social.SocialAuthProvider
import cloud.elsa.app.core.data.social.SocialProvider
import cloud.elsa.app.core.data.storage.login.LoginStorage
import cloud.elsa.app.core.data.storage.token.TokenStorage
import cloud.elsa.app.core.data.storage.user.UserStorage
import cloud.elsa.app.feature.settings.MemorySecureStorage
import cloud.elsa.app.feature.settings.MemoryStorage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 로그인된 상태의 세션 + 탈퇴 API 대역 */
internal class WithdrawFixture {
    val storage = MemoryStorage()
    val loginStorage = LoginStorage(storage)
    val tokenStorage = TokenStorage(MemorySecureStorage(), storage)
    val authStore = AuthStore()
    val userStore = UserStore()
    val session = SessionRepository(tokenStorage, UserStorage(storage), loginStorage, authStore, userStore)
    val unlinked = mutableListOf<SocialProvider>()

    var error: Throwable? = null
    val requests = mutableListOf<WithdrawRequest>()
    val repository = WithdrawRepository(
        api = object : WithdrawApi {
            override suspend fun withdraw(request: WithdrawRequest) {
                error?.let { throw it }
                requests += request
            }
        },
        sessionRepository = session,
        loginStorage = loginStorage,
        userStore = userStore,
        socialAuth = object : SocialAuthProvider {
            override fun signOut(provider: SocialProvider) = Unit
            override fun unlink(provider: SocialProvider) { unlinked += provider }
        },
    )

    suspend fun login(provider: SocialProvider? = null) {
        loginStorage.setSavedId("elsa")
        loginStorage.setRememberId(true)
        loginStorage.setAutoLogin(true)
        session.start(AuthSession("access", "refresh", UserModel(id = "elsa", name = "Elsa", provider = provider)))
    }
}

class WithdrawRepositoryTest {

    private val fixture = WithdrawFixture()

    @Test
    fun blankPasswordIsRejectedBeforeCallingApi() = runTest {
        fixture.login()

        val error = assertFailsWith<AppError.Validation> { fixture.repository.withdraw(" ") }

        assertEquals(ErrorCode.PasswordRequired, error.code)
        assertTrue(fixture.requests.isEmpty())
        assertEquals(AuthStatus.Authenticated, fixture.authStore.status.value)
    }

    @Test
    fun successClearsSessionAndSavedLogin() = runTest {
        fixture.login()

        fixture.repository.withdraw("password1")

        assertEquals(listOf(WithdrawRequest("password1")), fixture.requests)
        assertEquals(AuthStatus.Unauthenticated, fixture.authStore.status.value)
        assertNull(fixture.tokenStorage.getAccessToken())
        assertNull(fixture.loginStorage.getSavedId()) // 저장한 아이디도 남기지 않는다
        assertEquals(false, fixture.loginStorage.isAutoLogin())
    }

    @Test
    fun socialUserWithdrawsWithoutPasswordAndUnlinks() = runTest {
        fixture.login(provider = SocialProvider.Kakao)

        fixture.repository.withdraw(password = null)

        assertEquals(listOf(WithdrawRequest(password = null)), fixture.requests)
        assertEquals(listOf(SocialProvider.Kakao), fixture.unlinked)
        assertEquals(AuthStatus.Unauthenticated, fixture.authStore.status.value)
    }

    @Test
    fun idUserIsNotUnlinkedAndSocialPasswordIsNotSent() = runTest {
        fixture.login()
        fixture.repository.withdraw("password1")
        assertTrue(fixture.unlinked.isEmpty())

        val social = WithdrawFixture()
        social.login(provider = SocialProvider.Apple)
        social.repository.withdraw("ignored")
        assertEquals(listOf(WithdrawRequest(password = null)), social.requests) // 소셜 사용자의 비밀번호는 보내지 않는다
    }

    @Test
    fun serverFailureKeepsSession() = runTest {
        fixture.login()
        fixture.error = AppError.Http(403, "")

        assertFailsWith<AppError.Http> { fixture.repository.withdraw("wrong") }

        assertEquals(AuthStatus.Authenticated, fixture.authStore.status.value)
        assertTrue(fixture.unlinked.isEmpty())
        assertEquals("elsa", fixture.loginStorage.getSavedId())
    }
}
