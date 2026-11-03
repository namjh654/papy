package io.github.namjh654.papy.core.data.network

import io.github.namjh654.papy.core.common.error.AppError
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Serializable
private data class Echo(val value: String)

/** 요청을 기록하고 정해진 응답(또는 예외)을 돌려주는 HttpTransport. respond 는 이번 요청 헤더를 받는다 */
private class FakeTransport(var respond: (headers: Map<String, String>) -> String = { """{"value":"ok"}""" }) : HttpTransport {
    var lastUrl: String? = null
    var lastHeaders: Map<String, String> = emptyMap()
    var lastBody: String? = null

    override suspend fun request(method: HttpMethod, url: String, body: String?, headers: Map<String, String>): String {
        lastUrl = url
        lastHeaders = headers
        lastBody = body
        return respond(headers)
    }

    var lastFiles: List<UploadFile> = emptyList()
    var lastFields: Map<String, String> = emptyMap()

    override suspend fun upload(url: String, files: List<UploadFile>, fields: Map<String, String>, headers: Map<String, String>): String {
        lastUrl = url
        lastHeaders = headers
        lastFiles = files
        lastFields = fields
        return respond(headers)
    }
}

class BaseApiTest {

    private val config = ApiConfig(baseUrl = "https://api.example.com/", networkLog = false)

    @Test
    fun uploadSendsFilesWithTokenAndRetriesAfterRefresh() = runTest {
        var token = "old"
        val transport = FakeTransport { headers ->
            if (headers["Authorization"] == "Bearer old") throw AppError.Http(401, "") else """{"value":"uploaded"}"""
        }
        val handler = object : UnauthorizedHandler {
            override suspend fun refresh(failedToken: String): Boolean { token = "new"; return true }
            override suspend fun expire() = Unit
        }
        val api = BaseApi(config, transport, tokenProvider = { token }, unauthorizedHandler = handler)

        val response: Echo = api.upload(
            "/files",
            files = listOf(UploadFile("file", "a.jpg", "image/jpeg", byteArrayOf(1, 2))),
            fields = mapOf("kind" to "avatar"),
        )

        assertEquals("uploaded", response.value)
        assertEquals("https://api.example.com/files", transport.lastUrl)
        assertEquals("Bearer new", transport.lastHeaders["Authorization"]) // 재발급 뒤 다시 보냄
        assertEquals("a.jpg", transport.lastFiles.single().fileName)
        assertEquals(mapOf("kind" to "avatar"), transport.lastFields)
    }

    @Test
    fun buildsUrlAndEncodesQuery() = runTest {
        val transport = FakeTransport()
        BaseApi(config, transport).get<Echo>("/items", query = mapOf("q" to "a b&c", "page" to "1"))

        assertEquals("https://api.example.com/items?q=a%20b%26c&page=1", transport.lastUrl)
    }

    @Test
    fun attachesBearerTokenAndNoContentTypeHeader() = runTest {
        val transport = FakeTransport()
        BaseApi(config, transport, tokenProvider = { "t1" }).post<Echo, Echo>("items", Echo("x"))

        assertEquals("Bearer t1", transport.lastHeaders["Authorization"])
        assertNull(transport.lastHeaders["Content-Type"])
        assertEquals("""{"value":"x"}""", transport.lastBody)
    }

    /** refresh 결과를 정해 두고 호출을 기록하는 핸들러 */
    private class RecordingHandler(val refreshed: Boolean, val onRefresh: () -> Unit = {}) : UnauthorizedHandler {
        val refreshedTokens = mutableListOf<String>()
        var expired = 0
        override suspend fun refresh(failedToken: String): Boolean {
            refreshedTokens += failedToken
            onRefresh()
            return refreshed
        }
        override suspend fun expire() {
            expired++
        }
    }

    @Test
    fun unauthorizedRefreshesAndRetriesWithNewToken() = runTest {
        var token = "old"
        val transport = FakeTransport {
            if (it["Authorization"] == "Bearer old") throw AppError.Http(401, "") else """{"value":"ok"}"""
        }
        val handler = RecordingHandler(refreshed = true) { token = "new" }
        val api = BaseApi(config, transport, tokenProvider = { token }, unauthorizedHandler = handler)

        assertEquals(Echo("ok"), api.get<Echo>("me"))
        assertEquals(listOf("old"), handler.refreshedTokens)
        assertEquals("Bearer new", transport.lastHeaders["Authorization"])
        assertEquals(0, handler.expired)
    }

    @Test
    fun unauthorizedWhenRefreshFailsRethrows() = runTest {
        val transport = FakeTransport { throw AppError.Http(401, "") }
        val handler = RecordingHandler(refreshed = false)
        val api = BaseApi(config, transport, tokenProvider = { "t1" }, unauthorizedHandler = handler)

        val error = assertFailsWith<AppError.Http> { api.get<Echo>("me") }
        assertEquals(401, error.statusCode)
        assertEquals(listOf("t1"), handler.refreshedTokens)
    }

    @Test
    fun retryAlsoUnauthorizedExpiresSession() = runTest {
        val transport = FakeTransport { throw AppError.Http(401, "") }
        val handler = RecordingHandler(refreshed = true)
        val api = BaseApi(config, transport, tokenProvider = { "t1" }, unauthorizedHandler = handler)

        assertFailsWith<AppError.Http> { api.get<Echo>("me") }
        assertEquals(1, handler.refreshedTokens.size) // 재발급은 한 번만
        assertEquals(1, handler.expired)
    }

    @Test
    fun unauthorizedWithoutTokenDoesNotRefresh() = runTest {
        val transport = FakeTransport { throw AppError.Http(401, "") }
        val handler = RecordingHandler(refreshed = true)
        val api = BaseApi(config, transport, unauthorizedHandler = handler)

        assertFailsWith<AppError.Http> { api.get<Echo>("login") }
        assertTrue(handler.refreshedTokens.isEmpty())
    }

    @Test
    fun unitResponseIgnoresEmptyBody() = runTest {
        val transport = FakeTransport { "" }
        BaseApi(config, transport).delete<Unit>("items/1")

        assertEquals("https://api.example.com/items/1", transport.lastUrl)
    }

    @Test
    fun invalidJsonBecomesSerializationError() = runTest {
        val api = BaseApi(config, FakeTransport { "not json" })

        assertFailsWith<AppError.Serialization> { api.get<Echo>("items") }
    }
}
