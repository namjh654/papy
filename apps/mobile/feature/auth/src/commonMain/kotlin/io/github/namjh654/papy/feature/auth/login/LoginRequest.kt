package io.github.namjh654.papy.feature.auth.login

import kotlinx.serialization.Serializable

/** 로그인 API 요청 모델입니다. 플랫폼 UI 모델과 분리해 공통 전송 계약으로 사용합니다. */
@Serializable
data class LoginRequest(
    val id: String,
    val password: String,
)
