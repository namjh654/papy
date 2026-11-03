package cloud.elsa.app.feature.auth.login

import cloud.elsa.app.core.common.error.AppError
import cloud.elsa.app.core.common.error.ErrorCode
import cloud.elsa.app.core.data.session.AuthStatus
import cloud.elsa.app.feature.auth.FakeLoginApi
import cloud.elsa.app.feature.auth.TestSession
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class LoginRepositoryTest {

    private val session = TestSession()
    private val api = FakeLoginApi(
        LoginResponse(accessToken = "access", refreshToken = "refresh", user = LoginUser(id = "u1", displayName = "Elsa")),
    )
    private val repository = LoginRepository(api, session.loginStorage, session.repository)

    @Test
    fun blankCredentialsFailWithoutCallingApi() = runTest {
        val error = assertFailsWith<AppError.Validation> { repository.login(" ", "pw", rememberId = false, autoLogin = false) }

        assertEquals(ErrorCode.CredentialsRequired, error.code)
        assertNull(api.lastRequest)
    }

    @Test
    fun successStartsSessionAndSavesOptions() = runTest {
        val user = repository.login("elsa", "pw", rememberId = true, autoLogin = true)

        assertEquals("u1", user.id)
        assertEquals("Elsa", user.name)
        assertEquals(LoginRequest(id = "elsa", password = "pw"), api.lastRequest)
        assertEquals("access", session.tokenStorage.getAccessToken())
        assertEquals("refresh", session.tokenStorage.getRefreshToken())
        assertEquals(AuthStatus.Authenticated, session.authStore.status.value)
        assertEquals(LoginPreference(savedId = "elsa", rememberId = true, autoLogin = true), repository.getLoginPreference())
    }

    @Test
    fun legacyTokenFieldIsAccepted() = runTest {
        api.response = LoginResponse(token = "legacy", displayName = "Anna")

        val user = repository.login("anna", "pw", rememberId = false, autoLogin = false)

        assertEquals("Anna", user.name)
        assertEquals("legacy", session.tokenStorage.getAccessToken())
        assertEquals("", repository.getLoginPreference().savedId)
    }

    @Test
    fun unauthorizedLoginIsInvalidCredentialsNotSessionExpiry() = runTest {
        api.error = AppError.Http(401, "invalid credentials")

        val error = assertFailsWith<AppError.Validation> { repository.login("elsa", "wrong", rememberId = false, autoLogin = false) }

        assertEquals(ErrorCode.InvalidCredentials, error.code)
        assertEquals(AuthStatus.Unknown, session.authStore.status.value)
    }

    @Test
    fun otherHttpErrorsPassThrough() = runTest {
        api.error = AppError.Http(500, "boom")

        val error = assertFailsWith<AppError.Http> { repository.login("elsa", "pw", rememberId = false, autoLogin = false) }

        assertEquals(500, error.statusCode)
    }

    @Test
    fun missingTokenIsInvalidResponse() = runTest {
        api.response = LoginResponse()

        val error = assertFailsWith<AppError.Validation> { repository.login("elsa", "pw", rememberId = false, autoLogin = false) }

        assertEquals(ErrorCode.InvalidResponse, error.code)
        assertEquals(AuthStatus.Unknown, session.authStore.status.value)
    }
}
