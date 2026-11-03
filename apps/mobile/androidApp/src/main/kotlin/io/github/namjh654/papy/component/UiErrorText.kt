package io.github.namjh654.papy.component

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.namjh654.papy.R
import io.github.namjh654.papy.core.common.error.ErrorCode
import io.github.namjh654.papy.core.common.error.UiError

/** KMP 의 [ErrorCode] → 문자열 리소스. 코드가 추가되면 exhaustive when 이라 컴파일 오류로 드러난다. */
@StringRes
fun ErrorCode.toStringRes(): Int = when (this) {
    ErrorCode.CredentialsRequired -> R.string.error_credentials_required
    ErrorCode.UsernameRequired -> R.string.error_username_required
    ErrorCode.PasswordRequired -> R.string.error_password_required
    ErrorCode.PasswordMismatch -> R.string.error_password_mismatch
    ErrorCode.PasswordTooShort -> R.string.error_password_too_short
    ErrorCode.VerificationCodeRequired -> R.string.error_code_required
    ErrorCode.NameRequired -> R.string.error_name_required
    ErrorCode.InvalidEmail -> R.string.error_invalid_email
    ErrorCode.ContentRequired -> R.string.error_content_required
    ErrorCode.ContentTooLong -> R.string.error_content_too_long
    ErrorCode.TooManyImages -> R.string.error_too_many_images
    ErrorCode.InvalidInput -> R.string.error_invalid_input
    ErrorCode.SignupFailed -> R.string.error_signup_failed
    ErrorCode.InvalidCredentials -> R.string.error_invalid_credentials
    ErrorCode.SocialLoginFailed -> R.string.error_social_login_failed
    ErrorCode.Unauthorized -> R.string.error_unauthorized
    ErrorCode.RequestRejected -> R.string.error_request_rejected
    ErrorCode.ServerError -> R.string.error_server
    ErrorCode.Network -> R.string.error_network
    ErrorCode.InvalidResponse -> R.string.error_invalid_response
    ErrorCode.Unknown -> R.string.error_unknown
}

/** 화면에 표시할 오류 문구. HTTP 상태 코드가 있으면 문구 인자로 넘긴다(%1$d). */
@Composable
fun UiError.message(): String {
    val res = code.toStringRes()
    return statusCode?.let { stringResource(res, it) } ?: stringResource(res)
}

/** 토스트처럼 Composable 밖(이벤트 처리)에서 필요한 오류 문구: `val context = LocalContext.current` 를 넘긴다 */
fun UiError.message(context: Context): String {
    val res = code.toStringRes()
    return statusCode?.let { context.getString(res, it) } ?: context.getString(res)
}
