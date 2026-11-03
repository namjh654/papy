package io.github.namjh654.papy.feature.settings.profile

import io.github.namjh654.papy.core.data.network.BaseApi
import io.github.namjh654.papy.core.data.network.UploadFile
import io.github.namjh654.papy.core.data.session.UserModel
import kotlinx.serialization.Serializable

@Serializable
data class ProfileUpdateRequest(
    val name: String,
    val email: String? = null,
    val avatarUrl: String? = null,
)

@Serializable
data class AvatarResponse(val avatarUrl: String)

/**
 * 프로필 API 계약(가정): 사진은 먼저 올려 주소를 받고, 그 주소를 프로필 수정 요청에 싣는다.
 */
interface ProfileApi {
    /** 파일 업로드 기준 예시(multipart) */
    suspend fun uploadAvatar(bytes: ByteArray): AvatarResponse

    suspend fun update(request: ProfileUpdateRequest): UserModel
}

internal class RemoteProfileApi(
    private val baseApi: BaseApi,
) : ProfileApi {
    // {BASE_URL}/api/me/avatar (multipart: file)
    override suspend fun uploadAvatar(bytes: ByteArray): AvatarResponse = baseApi.upload(
        "/api/me/avatar",
        files = listOf(UploadFile(field = "file", fileName = "avatar.jpg", contentType = "image/jpeg", bytes = bytes)),
    )

    // {BASE_URL}/api/me
    override suspend fun update(request: ProfileUpdateRequest): UserModel = baseApi.put("/api/me", request)
}
