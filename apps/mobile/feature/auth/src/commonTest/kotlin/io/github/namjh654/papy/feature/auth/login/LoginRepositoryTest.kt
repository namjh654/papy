package io.github.namjh654.papy.feature.auth.login

import io.github.namjh654.papy.core.common.error.AppError
import io.github.namjh654.papy.core.common.error.ErrorCode
import io.github.namjh654.papy.core.data.session.AuthStatus
import io.github.namjh654.papy.feature.auth.FakeLoginApi
import io.github.namjh654.papy.feature.auth.TestSession
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class LoginRepositoryTest {

    private val session = TestSession()
    private val api = FakeLoginApi(
        LoginResponse(accessToken = "access", refreshToken = "refresh", user = LoginUser(id = "u1", displayName = "Papy")),
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
        val user = repository.login("papy", "pw", rememberId = true, autoLogin = true)

        assertEquals("u1", user.id)
        assertEquals("Papy", user.name)
        assertEquals(LoginRequest(id = "papy", password = "pw"), api.lastRequest)
        assertEquals("access", session.tokenStorage.getAccessToken())
        assertEquals("refresh", session.tokenStorage.getRefreshToken())
        assertEquals(AuthStatus.Authenticated, session.authStore.status.value)
        assertEquals(LoginPreference(savedId = "papy", rememberId = true, autoLogin = true), repository.getLoginPreference())
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

        val error = assertFailsWith<AppError.Validation> { repository.login("papy", "wrong", rememberId = false, autoLogin = false) }

        assertEquals(ErrorCode.InvalidCredentials, error.code)
        assertEquals(AuthStatus.Unknown, session.authStore.status.value)
    }

    @Test
    fun otherHttpErrorsPassThrough() = runTest {
        api.error = AppError.Http(500, "boom")

        val error = assertFailsWith<AppError.Http> { repository.login("papy", "pw", rememberId = false, autoLogin = false) }

        assertEquals(500, error.statusCode)
    }

    @Test
    fun missingTokenIsInvalidResponse() = runTest {
        api.response = LoginResponse()

        val error = assertFailsWith<AppError.Validation> { repository.login("papy", "pw", rememberId = false, autoLogin = false) }

        assertEquals(ErrorCode.InvalidResponse, error.code)
        assertEquals(AuthStatus.Unknown, session.authStore.status.value)
    }
}
