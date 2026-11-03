package cloud.elsa.app.feature.auth.reset

import cloud.elsa.app.core.data.network.BaseApi
import kotlinx.serialization.Serializable

@Serializable
data class PasswordResetCodeRequest(val username: String)

@Serializable
data class PasswordResetConfirmRequest(
    val username: String,
    val code: String,
    val password: String,
)

/**
 * 비밀번호 재설정 API 계약(가정): 아이디로 인증 코드를 요청하고, 코드 + 새 비밀번호로 확정한다.
 * 코드 전달 방법(메일·문자)은 서버가 정한다.
 */
interface PasswordResetApi {
    suspend fun requestCode(request: PasswordResetCodeRequest)
    suspend fun confirm(request: PasswordResetConfirmRequest)
}

internal class RemotePasswordResetApi(
    private val baseApi: BaseApi,
) : PasswordResetApi {
    // {BASE_URL}/api/auth/password/reset
    override suspend fun requestCode(request: PasswordResetCodeRequest): Unit =
        baseApi.post("/api/auth/password/reset", request)

    // {BASE_URL}/api/auth/password/reset/confirm
    override suspend fun confirm(request: PasswordResetConfirmRequest): Unit =
        baseApi.post("/api/auth/password/reset/confirm", request)
}
