package io.github.namjh654.papy.core.data.push

import io.github.namjh654.papy.core.data.network.BaseApi
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.Serializable

@Serializable
data class PushTokenRequest(
    val token: String,
    /** "android" | "ios" */
    val platform: String,
    /** [PushProvider.id] */
    val provider: String,
)

/** 서버 푸시 토큰 등록 API (서버 계약은 가정 — 서버와 맞춘 뒤 endpoint 만 바꾼다) */
interface PushTokenApi {
    suspend fun register(request: PushTokenRequest)
    suspend fun unregister(token: String)
}

internal class RemotePushTokenApi(
    private val baseApi: BaseApi,
) : PushTokenApi {
    // POST {BASE_URL}/api/push/tokens
    override suspend fun register(request: PushTokenRequest) =
        baseApi.post<PushTokenRequest, Unit>("/api/push/tokens", request)

    // DELETE {BASE_URL}/api/push/tokens/{token}
    override suspend fun unregister(token: String) =
        baseApi.delete<Unit>("/api/push/tokens/${token.encodeURLPathPart()}")
}
