package io.github.namjh654.papy.core.common.error

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.SerializationException

class AppErrorTest {

    @Test
    fun httpStatusMapsToErrorCode() {
        assertEquals(UiError(ErrorCode.Unauthorized, 401), AppError.Http(401, "").toUiError())
        assertEquals(UiError(ErrorCode.RequestRejected, 404), AppError.Http(404, "").toUiError())
        assertEquals(UiError(ErrorCode.ServerError, 503), AppError.Http(503, "").toUiError())
    }

    @Test
    fun exceptionsAreNormalized() {
        assertEquals(ErrorCode.InvalidInput, IllegalArgumentException("x").toAppError().code)
        assertEquals(ErrorCode.InvalidResponse, SerializationException("x").toAppError().code)
        assertEquals(ErrorCode.Unknown, IllegalStateException("x").toAppError().code)
    }

    @Test
    fun validationKeepsCodeWithoutStatus() {
        val error = AppError.Validation(ErrorCode.PasswordMismatch).toUiError()
        assertEquals(ErrorCode.PasswordMismatch, error.code)
        assertNull(error.statusCode)
    }
}
