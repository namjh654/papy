package io.github.namjh654.papy.core.data.network

/**
 * 모든 API 요청에 공통으로 적용할 헤더를 모읍니다.
 * Content-Type 은 여기서 넣지 않는다 — 본문이 있을 때 [KtorClient] 가 `application/json` 으로 한 번만 지정한다.
 */
data class ApiHeaders(
    val values: Map<String, String> = emptyMap(),
) {
    fun plus(name: String, value: String): ApiHeaders =
        ApiHeaders(values + (name to value))

    companion object {
        fun json(): ApiHeaders = ApiHeaders(mapOf("Accept" to "application/json"))
    }
}
