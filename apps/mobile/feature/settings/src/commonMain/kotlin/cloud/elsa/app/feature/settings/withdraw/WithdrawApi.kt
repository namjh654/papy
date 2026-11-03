package cloud.elsa.app.feature.settings.withdraw

import cloud.elsa.app.core.data.network.BaseApi
import kotlinx.serialization.Serializable

/** 소셜로 가입한 사용자는 비밀번호가 없어 null — 서버가 로그인된 토큰으로 확인한다(가정 계약, 18-4) */
@Serializable
data class WithdrawRequest(val password: String? = null)

/**
 * 회원 탈퇴 API 계약(가정): 본인 확인용 비밀번호(소셜 사용자는 없음)를 보내면 서버가 계정을 삭제한다.
 * 애플로 가입한 사용자는 서버가 가입 때 받은 인가 코드로 애플 토큰을 폐기한다(App Store 가이드라인 5.1.1(v))
 */
interface WithdrawApi {
    suspend fun withdraw(request: WithdrawRequest)
}

internal class RemoteWithdrawApi(
    private val baseApi: BaseApi,
) : WithdrawApi {
    // {BASE_URL}/api/account/withdraw
    override suspend fun withdraw(request: WithdrawRequest): Unit =
        baseApi.post("/api/account/withdraw", request)
}
