package io.github.namjh654.papy.core.data.network

import io.github.namjh654.papy.core.common.error.AppError
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException

/** 실제 Ktor HttpClient를 감싸서 BaseApi가 사용할 공통 HTTP 클라이언트입니다. */
internal class KtorClient(
    private val client: HttpClient,
) : HttpTransport {

    override suspend fun request(
        method: io.github.namjh654.papy.core.data.network.HttpMethod,
        url: String,
        body: String?,
        headers: Map<String, String>,
    ): String = perform {
        client.request(url) {
            this.method = when (method) {
                io.github.namjh654.papy.core.data.network.HttpMethod.Get -> HttpMethod.Get
                io.github.namjh654.papy.core.data.network.HttpMethod.Post -> HttpMethod.Post
                io.github.namjh654.papy.core.data.network.HttpMethod.Put -> HttpMethod.Put
                io.github.namjh654.papy.core.data.network.HttpMethod.Delete -> HttpMethod.Delete
            }
            applyHeaders(headers)
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
    }

    override suspend fun upload(
        url: String,
        files: List<UploadFile>,
        fields: Map<String, String>,
        headers: Map<String, String>,
    ): String = perform {
        client.submitFormWithBinaryData(
            url = url,
            formData = formData {
                fields.forEach { (name, value) -> append(name, value) }
                files.forEach { file ->
                    append(
                        file.field,
                        file.bytes,
                        Headers.build {
                            append(HttpHeaders.ContentType, file.contentType)
                            append(HttpHeaders.ContentDisposition, "filename=\"${file.fileName}\"")
                        },
                    )
                }
            },
        ) { applyHeaders(headers) }
    }

    /** Content-Type 은 본문 종류(JSON·multipart)가 정한다 — 공통 헤더에서 온 값은 버린다 */
    private fun HttpRequestBuilder.applyHeaders(values: Map<String, String>) {
        headers {
            values.filterKeys { !it.equals(HttpHeaders.ContentType, ignoreCase = true) }
                .forEach { (key, value) -> append(key, value) }
        }
    }

    /** 연결 실패 → [AppError.Network], 2xx 이외 → [AppError.Http] */
    private suspend fun perform(send: suspend () -> HttpResponse): String {
        val response = try {
            send()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw AppError.Network(e)
        }
        val responseBody = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw AppError.Http(response.status.value, responseBody)
        }
        return responseBody
    }
}
