package cloud.elsa.app.core.common.navigation

/**
 * 앱 안 화면을 가리키는 플랫폼 중립 주소. KMP 는 "어디로"의 의미만 주고,
 * 실제 화면 이동은 플랫폼 라우터(Android `DeepLinkRouter`, iOS `DeepLinkRouter`)가 [path] 로 정한다.
 *
 * 예: `elsa://example?id=1`, `https://app.example.com/example?id=1`, `/example?id=1` → `DeepLink("example", {id=1})`
 */
data class DeepLink(
    val path: String,
    val params: Map<String, String> = emptyMap(),
) {
    companion object {

        /** URI 문자열을 해석한다. 경로가 없으면 null. http(s) 는 호스트를 버리고 경로만, 그 밖의 scheme 은 호스트를 첫 경로로 본다. */
        fun parse(uri: String): DeepLink? {
            var rest = uri.trim().substringBefore('#')
            val schemeEnd = rest.indexOf("://")
            if (schemeEnd >= 0) {
                val scheme = rest.substring(0, schemeEnd).lowercase()
                rest = rest.substring(schemeEnd + 3)
                if (scheme == "http" || scheme == "https") rest = rest.substringAfter('/', "")
            }
            val path = rest.substringBefore('?').trim('/')
            if (path.isEmpty()) return null

            val params = rest.substringAfter('?', "")
                .split('&')
                .filter { it.isNotEmpty() }
                .associate { part -> decode(part.substringBefore('=')) to decode(part.substringAfter('=', "")) }
            return DeepLink(path, params)
        }

        /** query 값의 퍼센트 인코딩(UTF-8)과 '+'(공백)를 푼다 */
        private fun decode(value: String): String {
            if ('%' !in value && '+' !in value) return value
            val out = StringBuilder()
            val bytes = mutableListOf<Byte>()
            fun flush() {
                if (bytes.isNotEmpty()) {
                    out.append(bytes.toByteArray().decodeToString())
                    bytes.clear()
                }
            }
            var i = 0
            while (i < value.length) {
                val c = value[i]
                val hex = if (c == '%' && i + 2 < value.length) value.substring(i + 1, i + 3).toIntOrNull(16) else null
                if (hex != null) {
                    bytes += hex.toByte()
                    i += 3
                } else {
                    flush()
                    out.append(if (c == '+') ' ' else c)
                    i++
                }
            }
            flush()
            return out.toString()
        }
    }
}
