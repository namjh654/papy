package io.github.namjh654.papy.feature.auth.social

import io.github.namjh654.papy.core.data.network.BaseApi
import kotlinx.serialization.Serializable

/**
 * 소셜 로그인 API 계약(가정 — DEC-109, docs/known-limitations.md §1). 실제 구현은 [RemoteSocialLoginApi].
 * 서버 없이 개발할 때는 로컬 목업 서버(`mockServer`)가 같은 계약으로 응답한다.
 */
interface SocialLoginApi {
    suspend fun login(request: SocialLoginRequest): SocialLoginResponse
}

/** `POST /api/auth/social` 요청 */
@Serializable
data class SocialLoginRequest(
    val provider: String,
    val token: String,
    val authorizationCode: String? = null,
    val nonce: String? = null,
)

/** 응답: 우리 서비스 토큰 + 사용자. 처음 가입한 사용자면 [isNewUser] */
@Serializable
data class SocialLoginResponse(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val user: SocialLoginUser? = null,
    val isNewUser: Boolean = false,
)

@Serializable
data class SocialLoginUser(
    val id: String? = null,
    val displayName: String? = null,
    val email: String? = null,
)

internal class RemoteSocialLoginApi(
    private val baseApi: BaseApi,
) : SocialLoginApi {
    // {BASE_URL}/api/auth/social
    override suspend fun login(request: SocialLoginRequest): SocialLoginResponse =
        baseApi.post("/api/auth/social", request)
}
