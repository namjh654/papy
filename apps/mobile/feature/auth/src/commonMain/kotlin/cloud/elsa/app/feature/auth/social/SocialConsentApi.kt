package cloud.elsa.app.feature.auth.social

import cloud.elsa.app.core.data.network.BaseApi
import kotlinx.serialization.Serializable

/**
 * 소셜로 처음 가입한 사용자의 약관 동의 저장(가정 계약 — docs/known-limitations.md §1). 필수 약관은 동의해야만 화면이 부른다.
 */
interface SocialConsentApi {
    suspend fun agree(request: SocialConsentRequest)
}

@Serializable
data class SocialConsentRequest(val marketingAgreed: Boolean)

internal class RemoteSocialConsentApi(
    private val baseApi: BaseApi,
) : SocialConsentApi {
    // {BASE_URL}/api/auth/social/agreements
    override suspend fun agree(request: SocialConsentRequest): Unit =
        baseApi.post("/api/auth/social/agreements", request)
}
