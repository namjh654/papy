package cloud.elsa.app.core.data.network

import co.touchlab.kermit.Logger as KermitLogger
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpHeaders

/** Android는 OkHttp, iOS는 Darwin 엔진을 주입합니다. 공통 설정은 [installDefaults]. */
internal expect fun createPlatformHttpClient(config: ApiConfig): HttpClient

/**
 * 모든 엔진 공통 설정.
 * - 타임아웃: 연결 15초, 요청·소켓 30초 (초과 시 KtorClient 가 AppError.Network 로 변환)
 * - 로그: env 의 network.log 가 true 일 때만 요청 줄·헤더(본문 제외)를 Kermit(tag Http, debug)으로. Authorization 은 가린다
 * JSON 직렬화는 BaseApi 가 AppJson 으로 직접 하므로 ContentNegotiation 은 쓰지 않는다.
 */
internal fun HttpClientConfig<*>.installDefaults(config: ApiConfig) {
    install(HttpTimeout) {
        connectTimeoutMillis = 15_000
        requestTimeoutMillis = 30_000
        socketTimeoutMillis = 30_000
    }
    if (config.networkLog) {
        install(Logging) {
            logger = object : Logger {
                private val log = KermitLogger.withTag("Http")
                override fun log(message: String) = log.d { message }
            }
            level = LogLevel.HEADERS
            sanitizeHeader { header -> header == HttpHeaders.Authorization }
        }
    }
}
