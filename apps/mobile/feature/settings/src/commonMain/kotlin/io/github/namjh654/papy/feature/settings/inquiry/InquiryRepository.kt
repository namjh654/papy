package io.github.namjh654.papy.feature.settings.inquiry

import io.github.namjh654.papy.core.common.error.AppError
import io.github.namjh654.papy.core.common.error.ErrorCode

/** 문의 보내기: 검증(내용 필수·길이, 이메일 형식)한 뒤 앱 버전·플랫폼을 붙여 보낸다 */
class InquiryRepository(
    private val api: InquiryApi,
    private val appVersion: String,
    private val platform: String,
) {
    suspend fun submit(category: InquiryCategory, content: String, email: String) {
        val text = content.trim()
        val address = email.trim()
        if (text.isEmpty()) throw AppError.Validation(ErrorCode.ContentRequired)
        if (text.length > MAX_CONTENT) throw AppError.Validation(ErrorCode.ContentTooLong)
        if (address.isNotEmpty() && !EMAIL.matches(address)) throw AppError.Validation(ErrorCode.InvalidEmail)
        api.submit(
            InquiryRequest(category = category, content = text, email = address.ifEmpty { null }, appVersion = appVersion, platform = platform),
        )
    }

    companion object {
        const val MAX_CONTENT = 2_000
        private val EMAIL = Regex("""[^@\s]+@[^@\s]+\.[^@\s]+""")
    }
}
