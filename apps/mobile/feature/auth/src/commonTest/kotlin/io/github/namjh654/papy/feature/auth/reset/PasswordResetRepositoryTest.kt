package io.github.namjh654.papy.feature.auth.reset

import io.github.namjh654.papy.core.common.error.AppError
import io.github.namjh654.papy.core.common.error.ErrorCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PasswordResetRepositoryTest {

    private val api = FakePasswordResetApi()
    private val repository = PasswordResetRepository(api)

    @Test
    fun validatesBeforeCallingApi() = runTest {
        assertEquals(ErrorCode.UsernameRequired, assertFailsWith<AppError.Validation> { repository.requestCode(" ") }.code)
        assertEquals(
            ErrorCode.VerificationCodeRequired,
            assertFailsWith<AppError.Validation> { repository.confirm("u", "", "password1", "password1") }.code,
        )
        assertEquals(
            ErrorCode.PasswordTooShort,
            assertFailsWith<AppError.Validation> { repository.confirm("u", "123456", "short", "short") }.code,
        )
        assertEquals(
            ErrorCode.PasswordMismatch,
            assertFailsWith<AppError.Validation> { repository.confirm("u", "123456", "password1", "password2") }.code,
        )
        assertTrue(api.codeRequests.isEmpty() && api.confirms.isEmpty())
    }

    @Test
    fun trimsInputAndCallsApi() = runTest {
        repository.requestCode(" papy ")
        repository.confirm(" papy ", " 123456 ", "password1", "password1")

        assertEquals(listOf(PasswordResetCodeRequest("papy")), api.codeRequests)
        assertEquals(listOf(PasswordResetConfirmRequest("papy", "123456", "password1")), api.confirms)
    }
}

internal class FakePasswordResetApi(var error: Throwable? = null) : PasswordResetApi {
    val codeRequests = mutableListOf<PasswordResetCodeRequest>()
    val confirms = mutableListOf<PasswordResetConfirmRequest>()

    override suspend fun requestCode(request: PasswordResetCodeRequest) {
        error?.let { throw it }
        codeRequests += request
    }

    override suspend fun confirm(request: PasswordResetConfirmRequest) {
        error?.let { throw it }
        confirms += request
    }
}
