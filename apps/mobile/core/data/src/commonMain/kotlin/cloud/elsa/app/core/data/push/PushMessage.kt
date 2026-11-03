package cloud.elsa.app.core.data.push

import cloud.elsa.app.core.common.navigation.DeepLink

/** 받은 푸시 한 건. [deepLink] 가 있으면 탭했을 때 그 화면으로 이동한다 */
data class PushMessage(
    val title: String?,
    val body: String?,
    val data: Map<String, String>,
    val deepLink: DeepLink?,
)

/**
 * 푸시 data 해석 규칙 (서버와 맞출 계약):
 * - `link`   : 딥링크 URI (예: `elsa://example?id=1`) — 가장 우선
 * - `screen` : 화면 path (예: `example`) + 나머지 키는 params
 * - `title`, `body` : data-only 메시지의 표시 문구
 * 제공자 시스템 키(`google.*`, `gcm.*`, `from`, `collapse_key`, `aps`)는 버린다.
 */
object PushMessageParser {

    const val KEY_LINK = "link"
    const val KEY_SCREEN = "screen"
    const val KEY_TITLE = "title"
    const val KEY_BODY = "body"

    private val reserved = setOf(KEY_LINK, KEY_SCREEN, KEY_TITLE, KEY_BODY)
    private val systemKeys = setOf("from", "collapse_key", "aps")

    fun parse(data: Map<String, String>, title: String? = null, body: String? = null): PushMessage {
        val clean = data.filterKeys { key ->
            key !in systemKeys && !key.startsWith("google.") && !key.startsWith("gcm.")
        }
        val deepLink = clean[KEY_LINK]?.let(DeepLink::parse)
            ?: clean[KEY_SCREEN]?.trim('/')?.takeIf { it.isNotEmpty() }?.let { screen ->
                DeepLink(screen, clean.filterKeys { it !in reserved })
            }
        return PushMessage(
            title = title ?: clean[KEY_TITLE],
            body = body ?: clean[KEY_BODY],
            data = clean,
            deepLink = deepLink,
        )
    }
}
