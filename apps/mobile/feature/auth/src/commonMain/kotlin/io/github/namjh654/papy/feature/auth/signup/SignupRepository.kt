package io.github.namjh654.papy.feature.auth.signup

import io.github.namjh654.papy.core.common.error.AppError
import io.github.namjh654.papy.core.common.error.ErrorCode
import io.github.namjh654.papy.feature.auth.PasswordPolicy

class SignupRepository(
    private val signupApi: SignupApi,
) {

    suspend fun signup(
        username: String,
        password: String,
        passwordConfirmation: String,
        marketingAgreed: Boolean = false,
    ): SignupResponse {

        if (username.isBlank()) throw AppError.Validation(ErrorCode.UsernameRequired)
        PasswordPolicy.validate(password, passwordConfirmation)

        return signupApi.signup(
            SignupRequest(
                username = username,
                password = password,
                passwordConfirmation = passwordConfirmation,
                marketingAgreed = marketingAgreed,
            )
        )
    }
}
