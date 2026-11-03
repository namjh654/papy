package io.github.namjh654.papy.core.data.network

/** BaseApi가 Authorization 헤더에 사용할 토큰을 제공합니다. */
fun interface AccessTokenProvider {
    suspend fun accessToken(): String?
}
