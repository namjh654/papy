package template.tooling

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * 다국어 문자열 키 일치 검사 (DEC-018/021).
 * - Android: res/values/strings.xml(ko 기본) ↔ values-en/strings.xml
 * - iOS: iosApp/iosApp/Localizable.xcstrings (ko, en)
 *
 * ① Android ko/en 키 동일 ② Android 키가 모두 iOS 에 있음(iOS 전용 키 허용)
 * ③ iOS 모든 키에 ko/en 값 ④ 같은 키의 인자(%s, %1$d, %@, %lld …) 개수 동일 ⑤ 키는 snake_case (DEC-084)
 */
class StringsCheck(private val repo: Repo) {

    data class Result(val errors: List<String>, val summary: String)

    fun run(): Result {
        val errors = mutableListOf<String>()
        val ko = android(repo.file("androidApp/src/main/res/values/strings.xml"))
        val en = android(repo.file("androidApp/src/main/res/values-en/strings.xml"))
        (ko.keys union en.keys).subtract(ko.keys intersect en.keys).sorted()
            .forEach { errors += "Android ko/en 한쪽에만 있음: $it" }

        val catalog = Json.obj(Json.obj(Json.parse(repo.read(IOS)))["strings"])
        val ios = linkedMapOf<String, Map<String, String>>()
        catalog.forEach { (key, value) ->
            val locs = Json.obj(value)["localizations"]?.let(Json::obj) ?: emptyMap()
            val texts = mutableMapOf<String, String>()
            for (lang in listOf("ko", "en")) {
                val unit = locs[lang]?.let(Json::obj)?.get("stringUnit")?.let(Json::obj)
                if (unit == null) errors += "iOS $lang 값 없음: $key"
                texts[lang] = unit?.get("value") as? String ?: ""
            }
            ios[key] = texts
        }

        (ko.keys - ios.keys).sorted().forEach { errors += "iOS 에 없음: $it" }
        (ko.keys + ios.keys).filterNot { SNAKE.matches(it) }.sorted().forEach { errors += "키는 snake_case: $it" }
        (ko.keys intersect ios.keys).sorted().forEach { key ->
            val counts = listOf(ko.getValue(key), en[key] ?: "", ios.getValue(key).getValue("ko"), ios.getValue(key).getValue("en"))
                .map { ARG.findAll(it).count() }.toSet()
            if (counts.size > 1) errors += "인자 개수 불일치: $key"
        }
        val summary = "문자열 키 일치: Android ${ko.size} · iOS ${ios.size} (iOS 전용 ${(ios.keys - ko.keys).size})"
        return Result(errors, summary)
    }

    private fun android(file: File): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.documentElement.childNodes
        val map = linkedMapOf<String, String>()
        for (i in 0 until nodes.length) {
            val e = nodes.item(i) as? Element ?: continue
            if (e.tagName == "string" && e.getAttribute("translatable") != "false") map[e.getAttribute("name")] = e.textContent
        }
        return map
    }

    companion object {
        const val IOS = "iosApp/iosApp/Localizable.xcstrings"
        private val SNAKE = Regex("[a-z][a-z0-9]*(_[a-z0-9]+)*")
        private val ARG = Regex("""%(\d+\$)?(@|s|d|lld)""")
    }
}
