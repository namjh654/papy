package cloud.elsa.app.feature.auth.social

import cloud.elsa.app.core.common.error.AppError
import cloud.elsa.app.core.common.error.ErrorCode
import cloud.elsa.app.core.data.session.AuthStatus
import cloud.elsa.app.core.data.social.SocialCredential
import cloud.elsa.app.core.data.social.SocialProvider
import cloud.elsa.app.feature.auth.FakeSocialLoginApi
import cloud.elsa.app.feature.auth.TestSession
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SocialLoginRepositoryTest {

    private val session = TestSession()
    private val api = FakeSocialLoginApi()
    private val repository = SocialLoginRepository(api, session.loginStorage, session.repository)
    private val kakao = SocialCredential(provider = SocialProvider.Kakao, token = "kakao-token")

    @Test
    fun exchangeStartsSessionWithProvider() = runTest {
        val result = repository.login(kakao, autoLogin = true)

        assertEquals(SocialLoginRequest(provider = "kakao", token = "kakao-token"), api.lastRequest)
        assertEquals("s1", result.user.id)
        assertEquals(SocialProvider.Kakao, result.user.provider)
        assertFalse(result.isNewUser)
        assertEquals("social-access", session.tokenStorage.getAccessToken())
        assertEquals(AuthStatus.Authenticated, session.authStore.status.value)
        assertTrue(session.loginStorage.isAutoLogin())
        assertEquals("", session.loginStorage.getSavedId().orEmpty()) // 소셜은 아이디를 저장하지 않는다
    }

    @Test
    fun appleSendsAuthorizationCodeAndNonce() = runTest {
        repository.login(SocialCredential(SocialProvider.Apple, "id-token", authorizationCode = "code", nonce = "n"), autoLogin = false)

        assertEquals(SocialLoginRequest(provider = "apple", token = "id-token", authorizationCode = "code", nonce = "n"), api.lastRequest)
    }

    @Test
    fun newUserIsReported() = runTest {
        api.response = api.response.copy(isNewUser = true)

        assertTrue(repository.login(kakao, autoLogin = false).isNewUser)
    }

    @Test
    fun rejectedTokenIsSocialLoginFailedNotSessionExpiry() = runTest {
        api.error = AppError.Http(401, "invalid social token")

        val error = assertFailsWith<AppError.Validation> { repository.login(kakao, autoLogin = false) }

        assertEquals(ErrorCode.SocialLoginFailed, error.code)
        assertEquals(AuthStatus.Unknown, session.authStore.status.value)
    }

    @Test
    fun serverErrorPassesThrough() = runTest {
        api.error = AppError.Http(503, "down")

        assertEquals(503, assertFailsWith<AppError.Http> { repository.login(kakao, autoLogin = false) }.statusCode)
    }

    @Test
    fun missingTokenOrUserIsInvalidResponse() = runTest {
        api.response = api.response.copy(accessToken = null)
        assertEquals(ErrorCode.InvalidResponse, assertFailsWith<AppError.Validation> { repository.login(kakao, false) }.code)

        api.response = api.response.copy(accessToken = "a", user = null)
        assertEquals(ErrorCode.InvalidResponse, assertFailsWith<AppError.Validation> { repository.login(kakao, false) }.code)
    }

    @Test
    fun blankSdkTokenFailsWithoutCallingServer() = runTest {
        val error = assertFailsWith<AppError.Validation> { repository.login(kakao.copy(token = " "), autoLogin = false) }

        assertEquals(ErrorCode.SocialLoginFailed, error.code)
        assertNull(api.lastRequest)
    }
}
