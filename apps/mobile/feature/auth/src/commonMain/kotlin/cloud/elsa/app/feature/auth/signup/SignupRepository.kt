package cloud.elsa.app.feature.auth.signup

import cloud.elsa.app.core.common.error.AppError
import cloud.elsa.app.core.common.error.ErrorCode
import cloud.elsa.app.feature.auth.PasswordPolicy

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
