package io.github.namjh654.papy.feature.settings.inquiry

import io.github.namjh654.papy.core.data.network.BaseApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 문의 종류. Swift: `InquiryCategory.account` 등 — 문구는 플랫폼 리소스 */
@Serializable
enum class InquiryCategory {
    @SerialName("account") Account,
    @SerialName("bug") Bug,
    @SerialName("suggestion") Suggestion,
    @SerialName("other") Other,
}

/**
 * [appVersion]·[platform] 은 답변할 때 확인용으로 함께 보낸다. [email] 은 답장 받을 주소(비우면 계정 이메일·앱 알림으로 답한다 — 서버가 정한다)
 */
@Serializable
data class InquiryRequest(
    val category: InquiryCategory,
    val content: String,
    val email: String? = null,
    val appVersion: String,
    val platform: String,
)

/**
 * 문의하기 API 계약(가정, 25-3 — App Store 1.2 의 연락 수단): `POST /api/inquiries {category, content, email?, appVersion, platform}` → 204
 */
interface InquiryApi {
    suspend fun submit(request: InquiryRequest)
}

internal class RemoteInquiryApi(
    private val baseApi: BaseApi,
) : InquiryApi {
    override suspend fun submit(request: InquiryRequest) {
        baseApi.post<InquiryRequest, Unit>("/api/inquiries", body = request)
    }
}
