package cloud.elsa.app.core.data.session

import cloud.elsa.app.core.common.error.AppError
import cloud.elsa.app.core.data.network.BaseApi
import cloud.elsa.app.core.data.network.UnauthorizedHandler
import cloud.elsa.app.core.data.storage.token.TokenStorage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

/**
 * 토큰 재발급 API 계약 (가정 — DEC-055). 경로는 env 의 `auth.refreshPath`(비우면 재발급 안 함).
 * `POST {auth.refreshPath} {refreshToken}` → `{accessToken | token, refreshToken?}`
 */
interface TokenRefreshApi {
    suspend fun refresh(refreshToken: String): TokenRefreshResponse
}

@Serializable
data class TokenRefreshRequest(val refreshToken: String)

@Serializable
data class TokenRefreshResponse(
    val accessToken: String? = null,
    val token: String? = null,
    val refreshToken: String? = null,
)

/** 재발급 요청은 토큰·401 처리가 없는 BaseApi 로 보낸다(재발급 안에서 다시 재발급하지 않게). */
internal class RemoteTokenRefreshApi(
    private val baseApi: BaseApi,
    private val path: String,
) : TokenRefreshApi {
    override suspend fun refresh(refreshToken: String): TokenRefreshResponse =
        baseApi.post(path, TokenRefreshRequest(refreshToken))
}

/**
 * 401 처리 (DEC-045): 재발급 → 원래 요청 1회 재시도. 동시에 401 을 받은 요청들은 재발급을 한 번만 한다.
 *
 * - 재발급 API 가 없거나(api = null) refresh token 이 없으면 → 세션 만료(로그인 화면)
 * - 서버가 재발급을 거절(4xx)하거나 응답에 토큰이 없으면 → 세션 만료
 * - 네트워크·5xx 로 재발급을 못 하면 → 세션 유지, 그 오류를 원래 요청의 오류로 던진다(재시도 가능)
 * - 재발급한 토큰으로 다시 보낸 요청도 401 이면 → 세션 만료 ([expire])
 *
 * @param expireSession 보통 SessionRepository.expire (토큰·사용자 삭제 → AuthStore Unauthenticated)
 */
class TokenRefresher(
    private val tokenStorage: TokenStorage,
    private val api: TokenRefreshApi?,
    private val expireSession: suspend () -> Unit,
) : UnauthorizedHandler {

    private val mutex = Mutex()

    override suspend fun refresh(failedToken: String): Boolean = mutex.withLock {
        // 기다리는 동안 다른 요청이 이미 재발급했거나(토큰이 바뀜) 세션을 끝냈다(토큰 없음)
        val current = tokenStorage.getAccessToken() ?: return false
        if (current != failedToken) return true

        val refreshToken = tokenStorage.getRefreshToken()
        if (api == null || refreshToken == null) {
            expireSession()
            return false
        }
        val response = try {
            api.refresh(refreshToken)
        } catch (e: AppError.Http) {
            if (e.statusCode in 400..499) {
                expireSession()
                return false
            }
            throw e
        } catch (e: AppError.Serialization) {
            expireSession()
            return false
        }
        val newToken = response.accessToken ?: response.token
        if (newToken.isNullOrBlank()) {
            expireSession()
            return false
        }
        tokenStorage.setAccessToken(newToken)
        response.refreshToken?.let { tokenStorage.setRefreshToken(it) }
        true
    }

    override suspend fun expire() {
        expireSession()
    }
}
