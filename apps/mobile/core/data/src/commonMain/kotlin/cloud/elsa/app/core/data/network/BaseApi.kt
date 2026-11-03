package cloud.elsa.app.core.data.network

import cloud.elsa.app.core.common.error.AppError
import cloud.elsa.app.core.data.serialization.AppJson
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * 인증 헤더를 붙인 요청이 401 을 받았을 때 BaseApi 가 호출한다 (DEC-045).
 * 앱 구현은 [cloud.elsa.app.core.data.session.TokenRefresher](재발급 → 재시도, 실패 시 세션 만료).
 */
interface UnauthorizedHandler {
    /** [failedToken] 으로 401. 새 토큰이 저장됐으면 true(원래 요청을 한 번 다시 보낸다), 세션을 끝냈으면 false */
    suspend fun refresh(failedToken: String): Boolean

    /** 새 토큰으로 다시 보낸 요청도 401 → 세션을 끝낸다 */
    suspend fun expire()

    /** 401 을 그대로 던지기만 한다(테스트·토큰 없는 API) */
    object None : UnauthorizedHandler {
        override suspend fun refresh(failedToken: String) = false
        override suspend fun expire() = Unit
    }
}

/**
 * 모든 API 호출의 공통 진입점. feature 의 `XxxApi` 는 이 클래스의 get/post/put/delete/upload 만 사용한다.
 *
 * - URL: `ApiConfig.baseUrl` + endpoint + query(URL 인코딩)
 * - 헤더: `ApiHeaders.json()` + 토큰이 있으면 `Authorization: Bearer …`
 * - 응답: [AppJson] 으로 디코딩, 실패 시 [AppError.Serialization]. 응답 타입이 `Unit` 이면 본문을 읽지 않는다(204 등)
 * - 401: 토큰을 붙인 요청이면 [UnauthorizedHandler.refresh] → 새 토큰으로 1회 재시도. 재발급 실패·재시도도 401 이면 [AppError.Http] 를 던진다
 */
class BaseApi(
    private val config: ApiConfig,
    private val transport: HttpTransport,
    private val tokenProvider: AccessTokenProvider = AccessTokenProvider { null },
    private val unauthorizedHandler: UnauthorizedHandler = UnauthorizedHandler.None,
    @PublishedApi internal val json: Json = AppJson,
    @PublishedApi internal val commonHeaders: ApiHeaders = ApiHeaders.json(),
) {

    suspend inline fun <reified Response : Any> get(
        endpoint: String,
        query: Map<String, String> = emptyMap(),
        headers: ApiHeaders = commonHeaders,
    ): Response = decode(execute(HttpMethod.Get, endpoint, headers = headers, query = query))

    suspend inline fun <reified Request : Any, reified Response : Any> post(
        endpoint: String,
        body: Request,
        headers: ApiHeaders = commonHeaders,
    ): Response = decode(execute(HttpMethod.Post, endpoint, body = json.encodeToString(body), headers = headers))

    suspend inline fun <reified Request : Any, reified Response : Any> put(
        endpoint: String,
        body: Request,
        headers: ApiHeaders = commonHeaders,
    ): Response = decode(execute(HttpMethod.Put, endpoint, body = json.encodeToString(body), headers = headers))

    suspend inline fun <reified Response : Any> delete(
        endpoint: String,
        headers: ApiHeaders = commonHeaders,
    ): Response = decode(execute(HttpMethod.Delete, endpoint, headers = headers))

    /**
     * 파일 업로드(multipart/form-data POST). 토큰·401 재시도는 다른 요청과 같다.
     * ```
     * baseApi.upload<AvatarResponse>("/api/me/avatar", files = listOf(UploadFile("file", "avatar.jpg", "image/jpeg", bytes)))
     * ```
     */
    suspend inline fun <reified Response : Any> upload(
        endpoint: String,
        files: List<UploadFile>,
        fields: Map<String, String> = emptyMap(),
        headers: ApiHeaders = commonHeaders,
    ): Response = decode(executeUpload(endpoint, files, fields, headers))

    @PublishedApi
    internal inline fun <reified Response : Any> decode(response: String): Response {
        if (Response::class == Unit::class) return Unit as Response
        return try {
            json.decodeFromString(response)
        } catch (e: SerializationException) {
            throw AppError.Serialization(e)
        }
    }

    @PublishedApi
    internal suspend fun execute(
        method: HttpMethod,
        endpoint: String,
        body: String? = null,
        headers: ApiHeaders = commonHeaders,
        query: Map<String, String> = emptyMap(),
    ): String {
        val url = buildUrl(endpoint, query)
        return withAuthorization(headers) { authorized ->
            transport.request(method = method, url = url, body = body, headers = authorized)
        }
    }

    @PublishedApi
    internal suspend fun executeUpload(
        endpoint: String,
        files: List<UploadFile>,
        fields: Map<String, String>,
        headers: ApiHeaders,
    ): String {
        val url = buildUrl(endpoint, emptyMap())
        return withAuthorization(headers) { authorized ->
            transport.upload(url = url, files = files, fields = fields, headers = authorized)
        }
    }

    /** 토큰을 붙여 보내고, 401 이면 재발급 뒤 한 번 다시 보낸다. [request] 는 최종 헤더를 받아 실제 요청을 한다 */
    private suspend fun withAuthorization(
        headers: ApiHeaders,
        request: suspend (headers: Map<String, String>) -> String,
    ): String {
        // 호출하는 쪽이 Authorization 을 직접 넣었으면 토큰·401 처리를 하지 않는다
        val token = if (AUTHORIZATION in headers.values) null else tokenProvider.accessToken()
        suspend fun send(token: String?): String {
            val authorized = token?.let { headers.plus(AUTHORIZATION, "Bearer $it") } ?: headers
            return request(authorized.values)
        }
        try {
            return send(token)
        } catch (e: AppError.Http) {
            if (!e.isUnauthorized || token == null || !unauthorizedHandler.refresh(token)) throw e
        }
        // 재발급 성공 → 새 토큰으로 한 번만 다시 보낸다
        try {
            return send(tokenProvider.accessToken())
        } catch (e: AppError.Http) {
            if (e.isUnauthorized) unauthorizedHandler.expire()
            throw e
        }
    }

    internal fun buildUrl(endpoint: String, query: Map<String, String>): String {
        val url = config.baseUrl.trimEnd('/') + "/" + endpoint.trimStart('/')
        if (query.isEmpty()) return url
        return url + "?" + query.entries.joinToString("&") { (key, value) ->
            key.encodeURLParameter() + "=" + value.encodeURLParameter()
        }
    }

    private companion object {
        const val AUTHORIZATION = "Authorization"
    }
}
