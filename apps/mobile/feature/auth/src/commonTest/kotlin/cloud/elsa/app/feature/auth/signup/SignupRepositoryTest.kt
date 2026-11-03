package cloud.elsa.app.feature.auth.signup

import cloud.elsa.app.core.common.error.AppError
import cloud.elsa.app.core.common.error.ErrorCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SignupRepositoryTest {

    private var called = false
    private val repository = SignupRepository(object : SignupApi {
        override suspend fun signup(request: SignupRequest): SignupResponse {
            called = true
            return SignupResponse(success = true)
        }
    })

    @Test
    fun validatesBeforeCallingApi() = runTest {
        assertEquals(ErrorCode.UsernameRequired, assertFailsWith<AppError.Validation> { repository.signup("", "a", "a") }.code)
        assertEquals(ErrorCode.PasswordRequired, assertFailsWith<AppError.Validation> { repository.signup("u", "", "") }.code)
        assertEquals(ErrorCode.PasswordMismatch, assertFailsWith<AppError.Validation> { repository.signup("u", "password1", "password2") }.code)
        assertEquals(ErrorCode.PasswordTooShort, assertFailsWith<AppError.Validation> { repository.signup("u", "short", "short") }.code)
        assertEquals(false, called)
    }

    @Test
    fun validInputCallsApi() = runTest {
        assertTrue(repository.signup("u", "password1", "password1").success)
        assertTrue(called)
    }
}
