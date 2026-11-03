package cloud.elsa.app.core.data.files

import cloud.elsa.app.core.common.error.AppError
import cloud.elsa.app.core.data.network.AccessTokenProvider
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.exhausted
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlinx.coroutines.CancellationException
import okio.BufferedSink

/**
 * 파일 바이트를 받는다(조각으로 흘려 써서 큰 파일도 메모리에 다 올리지 않는다).
 * 토큰은 우리 서버([apiBaseUrl]) 주소일 때만 붙인다 — 다른 저장소(CDN 등) 주소로 토큰이 나가지 않게.
 */
internal class KtorFileTransport(
    private val httpClient: HttpClient,
    private val apiBaseUrl: String,
    private val tokenProvider: AccessTokenProvider,
) : FileTransport {

    override suspend fun download(url: String, sink: BufferedSink, onProgress: (Long, Long?) -> Unit) {
        val token = if (apiBaseUrl.isNotBlank() && url.startsWith(apiBaseUrl)) tokenProvider.accessToken() else null
        val statement = try {
            httpClient.prepareGet(url) { token?.let { bearerAuth(it) } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw AppError.Network(e)
        }
        try {
            statement.execute { response ->
                if (!response.status.isSuccess()) throw AppError.Http(response.status.value, "")
                val total = response.contentLength()
                val channel = response.bodyAsChannel()
                var read = 0L
                while (!channel.exhausted()) {
                    val chunk = channel.readRemaining(CHUNK).readByteArray()
                    sink.write(chunk)
                    read += chunk.size
                    onProgress(read, total)
                }
                sink.flush()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: AppError) {
            throw e
        } catch (e: Throwable) {
            throw AppError.Network(e)
        }
    }

    private companion object {
        const val CHUNK = 64 * 1024L
    }
}
