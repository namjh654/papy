package io.github.namjh654.papy.core.data.session

import io.github.namjh654.papy.core.data.social.SocialProvider
import kotlinx.serialization.Serializable

@Serializable
data class UserModel(
    val id: String,
    val name: String,
    val email: String? = null,
    /** 프로필 사진 주소(없으면 이름 첫 글자를 보여준다) */
    val avatarUrl: String? = null,
    /** 소셜로 로그인했으면 그 제공자(로그아웃 때 SDK 세션도 끝낸다), 아이디 로그인은 null — DEC-107 */
    val provider: SocialProvider? = null,
)