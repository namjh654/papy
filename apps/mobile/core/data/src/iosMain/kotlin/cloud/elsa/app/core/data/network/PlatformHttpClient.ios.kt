package cloud.elsa.app.core.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

internal actual fun createPlatformHttpClient(config: ApiConfig): HttpClient = HttpClient(Darwin) {
    installDefaults(config)
}
