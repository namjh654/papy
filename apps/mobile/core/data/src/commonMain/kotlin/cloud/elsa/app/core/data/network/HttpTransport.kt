package cloud.elsa.app.core.data.network

enum class HttpMethod {
    Get,
    Post,
    Put,
    Delete,
}

/**
 * multipart 로 보낼 파일 하나.
 *
 * @property field 폼 필드 이름(서버가 정한다. 예: "file", "avatar")
 * @property contentType 예: "image/jpeg"
 */
class UploadFile(
    val field: String,
    val fileName: String,
    val contentType: String,
    val bytes: ByteArray,
)

interface HttpTransport {
    suspend fun request(
        method: HttpMethod,
        url: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): String

    /** multipart/form-data POST. 업로드를 쓰지 않는 테스트 대역은 구현하지 않아도 된다 */
    suspend fun upload(
        url: String,
        files: List<UploadFile>,
        fields: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
    ): String = throw UnsupportedOperationException("upload 를 구현하지 않은 HttpTransport")
}
