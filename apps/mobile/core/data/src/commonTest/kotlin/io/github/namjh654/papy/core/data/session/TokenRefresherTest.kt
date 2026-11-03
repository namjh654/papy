package io.github.namjh654.papy.core.data.session

import io.github.namjh654.papy.core.common.error.AppError
import io.github.namjh654.papy.core.data.network.ApiConfig
import io.github.namjh654.papy.core.data.network.BaseApi
import io.github.namjh654.papy.core.data.network.HttpMethod
import io.github.namjh654.papy.core.data.network.HttpTransport
import io.github.namjh654.papy.core.data.storage.FakeLocalStorage
import io.github.namjh654.papy.core.data.storage.FakeSecureStorage
import io.github.namjh654.papy.core.data.storage.token.TokenStorage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TokenRefresherTest {

    private val tokens = TokenStorage(FakeSecureStorage(), FakeLocalStorage())
    private var expired = 0

    private class FakeRefreshApi(var respond: suspend (String) -> TokenRefreshResponse) : TokenRefreshApi {
        var calls = 0
        override suspend fun refresh(refreshToken: String): TokenRefreshResponse {
            calls++
            return respond(refreshToken)
        }
    }

    private suspend fun signedIn(access: String = "a1", refresh: String? = "r1") {
        tokens.setAccessToken(access)
        refresh?.let { tokens.setRefreshToken(it) }
    }

    private fun refresher(api: TokenRefreshApi?) = TokenRefresher(tokens, api) {
        expired++
        tokens.clear()
    }

    @Test
    fun refreshStoresNewTokens() = runTest {
        signedIn()
        val api = FakeRefreshApi { TokenRefreshResponse(accessToken = "a2", refreshToken = "r2") }

        assertTrue(refresher(api).refresh("a1"))
        assertEquals("a2", tokens.getAccessToken())
        assertEquals("r2", tokens.getRefreshToken())
        assertEquals(0, expired)
    }

    @Test
    fun noRefreshApiExpiresImmediately() = runTest {
        signedIn()
        assertFalse(refresher(api = null).refresh("a1"))
        assertEquals(1, expired)
        assertNull(tokens.getAccessToken())
    }

    @Test
    fun rejectedRefreshExpires() = runTest {
        signedIn()
        val api = FakeRefreshApi { throw AppError.Http(401, "") }

        assertFalse(refresher(api).refresh("a1"))
        assertEquals(1, expired)
    }

    @Test
    fun networkFailureKeepsSessionAndRethrows() = runTest {
        signedIn()
        val api = FakeRefreshApi { throw AppError.Network(RuntimeException("offline")) }

        assertFailsWith<AppError.Network> { refresher(api).refresh("a1") }
        assertEquals(0, expired)
        assertEquals("a1", tokens.getAccessToken())
    }

    @Test
    fun concurrentUnauthorizedRefreshesOnce() = runTest {
        signedIn()
        val gate = CompletableDeferred<Unit>()
        val api = FakeRefreshApi { gate.await(); TokenRefreshResponse(token = "a2") }
        val refresher = refresher(api)

        val results = List(3) { async { refresher.refresh("a1") } }
        testScheduler.advanceUntilIdle()
        gate.complete(Unit)

        assertEquals(listOf(true, true, true), results.awaitAll())
        assertEquals(1, api.calls)
        assertEquals("a2", tokens.getAccessToken())
    }

    /** BaseApi + TokenRefresher 를 함께: 401 → 재발급 → 재시도, 동시 요청도 재발급 1회 */
    @Test
    fun baseApiRetriesConcurrentRequestsWithOneRefresh() = runTest {
        signedIn()
        val api = FakeRefreshApi { TokenRefreshResponse(accessToken = "a2") }
        val transport = object : HttpTransport {
            override suspend fun request(method: HttpMethod, url: String, body: String?, headers: Map<String, String>): String {
                if (headers["Authorization"] != "Bearer a2") throw AppError.Http(401, "")
                return """{"value":"ok"}"""
            }
        }
        val baseApi = BaseApi(
            config = ApiConfig(baseUrl = "https://api.example.com", networkLog = false),
            transport = transport,
            tokenProvider = { tokens.getAccessToken() },
            unauthorizedHandler = refresher(api),
        )

        val results = List(3) { async { baseApi.get<Value>("me") } }.awaitAll()

        assertEquals(List(3) { Value("ok") }, results)
        assertEquals(1, api.calls)
        assertEquals(0, expired)
    }

    @Serializable
    private data class Value(val value: String)
}
