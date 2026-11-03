package io.github.namjh654.papy.feature.auth.login

import kotlinx.serialization.Serializable

/** 로그인 API 응답 모델입니다. 서버 응답의 선택 필드를 안전하게 수용합니다. */
@Serializable
data class LoginResponse(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val displayName: String? = null,
    val token: String? = null,
    val user: LoginUser? = null,
)

@Serializable
data class LoginUser(
    val id: String? = null,
    val displayName: String? = null,
    val username: String? = null,
    val email: String? = null,
)
