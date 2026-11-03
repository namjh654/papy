package cloud.elsa.app.feature.auth

import cloud.elsa.app.core.common.error.AppError
import cloud.elsa.app.core.common.error.ErrorCode

/** 새 비밀번호 규칙 한 곳(회원가입·비밀번호 재설정이 같이 쓴다). 규칙을 바꾸면 문구 `error_password_too_short` 도 맞춘다 */
internal object PasswordPolicy {
    const val MIN_LENGTH = 8

    /** 어긋나면 [AppError.Validation] 을 던진다 */
    fun validate(password: String, confirmation: String) {
        when {
            password.isBlank() -> throw AppError.Validation(ErrorCode.PasswordRequired)
            password.length < MIN_LENGTH -> throw AppError.Validation(ErrorCode.PasswordTooShort)
            password != confirmation -> throw AppError.Validation(ErrorCode.PasswordMismatch)
        }
    }
}
