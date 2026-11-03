package io.github.namjh654.papy.core.common.error

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException

/**
 * Business Layer 공통 에러 타입입니다.
 *
 * Network/Repository에서 발생한 예외는 모두 [AppError]로 정규화되어 ViewModel에 전달되고,
 * ViewModel은 [toUiError] 결과를 UiState에 담고, 문구는 Native UI가 [ErrorCode]로 결정합니다(다국어).
 */
sealed class AppError(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    /** 2xx 이외의 HTTP 응답입니다. */
    class Http(
        val statusCode: Int,
        val body: String,
    ) : AppError("HTTP $statusCode: $body")

    /** 연결 실패, 타임아웃 등 서버 응답을 받지 못한 경우입니다. */
    class Network(cause: Throwable) : AppError(cause.message ?: "Network error", cause)

    /** 응답 JSON이 계약과 다른 경우입니다. */
    class Serialization(cause: Throwable) : AppError(cause.message ?: "Serialization error", cause)

    /** 입력값 검증 실패 또는 응답 필수 값 누락입니다. */
    class Validation(val reason: ErrorCode) : AppError(reason.name)

    class Unknown(cause: Throwable) : AppError(cause.message ?: "Unknown error", cause)

    val isUnauthorized: Boolean
        get() = this is Http && statusCode == 401

    val code: ErrorCode
        get() = when (this) {
            is Http -> when (statusCode) {
                401 -> ErrorCode.Unauthorized
                in 400..499 -> ErrorCode.RequestRejected
                else -> ErrorCode.ServerError
            }
            is Network -> ErrorCode.Network
            is Serialization -> ErrorCode.InvalidResponse
            is Validation -> reason
            is Unknown -> ErrorCode.Unknown
        }

    fun toUiError(): UiError = UiError(
        code = code,
        statusCode = (this as? Http)?.statusCode,
    )
}

/** 임의의 예외를 [AppError]로 정규화합니다. 코루틴 취소는 그대로 전파합니다. */
fun Throwable.toAppError(): AppError = when (this) {
    is CancellationException -> throw this
    is AppError -> this
    is SerializationException -> AppError.Serialization(this)
    is IllegalArgumentException -> AppError.Validation(ErrorCode.InvalidInput)
    else -> AppError.Unknown(this)
}
