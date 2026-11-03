package io.github.namjh654.papy.feature.auth.reset

import io.github.namjh654.papy.core.common.error.AppError
import io.github.namjh654.papy.core.common.error.ErrorCode
import io.github.namjh654.papy.feature.auth.PasswordPolicy

class PasswordResetRepository(
    private val api: PasswordResetApi,
) {

    suspend fun requestCode(username: String) {
        if (username.isBlank()) throw AppError.Validation(ErrorCode.UsernameRequired)
        api.requestCode(PasswordResetCodeRequest(username.trim()))
    }

    suspend fun confirm(username: String, code: String, password: String, passwordConfirmation: String) {
        if (code.isBlank()) throw AppError.Validation(ErrorCode.VerificationCodeRequired)
        PasswordPolicy.validate(password, passwordConfirmation)
        api.confirm(PasswordResetConfirmRequest(username.trim(), code.trim(), password))
    }
}
