package cloud.elsa.app.core.data.network

import cloud.elsa.app.core.data.config.AppConfig

/**
 * 네트워크 설정. 기본값은 이번 빌드 환경의 [AppConfig](env/<env>.properties 에서 생성) — 코드는 환경 이름을 모른다.
 * 테스트는 값을 직접 넣어 만든다.
 */
data class ApiConfig(
    val baseUrl: String = AppConfig.API_BASE_URL,
    /** 요청 로그(Authorization 가림) — env 의 network.log */
    val networkLog: Boolean = AppConfig.NETWORK_LOG,
)
