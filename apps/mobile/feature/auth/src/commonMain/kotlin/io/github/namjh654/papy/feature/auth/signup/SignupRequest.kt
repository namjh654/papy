package io.github.namjh654.papy.feature.auth.signup

import kotlinx.serialization.Serializable

@Serializable
data class SignupRequest(
    val username: String,
    val password: String,
    val passwordConfirmation: String,
    /** 약관 화면의 선택 동의(마케팅 정보 수신) */
    val marketingAgreed: Boolean = false,
)
