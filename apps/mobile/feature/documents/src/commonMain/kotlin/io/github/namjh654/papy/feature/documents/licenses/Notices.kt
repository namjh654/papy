package io.github.namjh654.papy.feature.documents.licenses

/** 서드파티 고지 한 항목: 구성요소 이름, 라이선스 식별자, 본문(저작권·라이선스 전문) */
data class Notice(val title: String, val license: String, val text: String)

/** 고지 파일 전체: 머리말(Papy 라이선스 안내)과 항목들 */
data class Notices(val header: String, val items: List<Notice>)

private const val RULE = "=============================================================================="

/**
 * `scripts/third-party-notices.mjs mobile` 이 만든 THIRD_PARTY_NOTICES.txt 를 항목별로 나눈다.
 * 항목 모양: 구분선 / 이름 / `License: …` / 구분선 / 본문.
 * "same text as X" 로 다른 항목을 가리키는 줄은 그대로 둔다(화면에서 이름으로 찾아갈 수 있다).
 */
fun parseNotices(text: String): Notices {
    val parts = text.replace("\r\n", "\n").split("\n$RULE\n")
    val header = parts.first().trim()
    val items = mutableListOf<Notice>()
    // parts: [머리말, 이름+License, 본문, 이름+License, 본문, …]. 본문 안에도 같은 구분선이 있을 수 있어
    // 이름 다음 줄이 `License: ` 인 조각만 새 항목으로 본다.
    var title: String? = null
    var license = ""
    val body = StringBuilder()
    fun flush() {
        title?.let { items += Notice(it, license, body.toString().trim()) }
        body.clear()
    }
    for (part in parts.drop(1)) {
        val lines = part.lines()
        val head = lines.getOrNull(1)?.startsWith("License: ") == true && lines.size <= 3
        if (head) {
            flush()
            title = lines.first().trim()
            license = lines[1].removePrefix("License: ").trim()
        } else if (title != null) {
            if (body.isNotEmpty()) body.append("\n").append(RULE).append("\n")
            body.append(part)
        }
    }
    flush()
    return Notices(header, items)
}
