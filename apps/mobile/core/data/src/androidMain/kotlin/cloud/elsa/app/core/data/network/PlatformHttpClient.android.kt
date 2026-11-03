package cloud.elsa.app.core.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

internal actual fun createPlatformHttpClient(config: ApiConfig): HttpClient = HttpClient(OkHttp) {
    installDefaults(config)
}
