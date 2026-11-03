package template.tooling

/**
 * 의존성 없는 최소 JSON 파서/출력기.
 * - 객체는 키 순서를 지키는 LinkedHashMap, 배열은 List, 숫자는 Long(정수) 또는 Double
 * - [writeXcode] 는 Xcode 의 .xcstrings 형식(2칸 들여쓰기, `"key" : value`, 키 정렬)으로 쓴다
 */
object Json {

    fun parse(text: String): Any? = Parser(text).run {
        val value = readValue()
        skipWhitespace()
        require(pos == text.length) { "JSON 끝에 남은 문자 (위치 $pos)" }
        value
    }

    @Suppress("UNCHECKED_CAST")
    fun obj(value: Any?): Map<String, Any?> = value as? Map<String, Any?> ?: error("JSON 객체가 아님: $value")

    /** Xcode(.xcstrings) 와 같은 형식. 끝에 줄바꿈 포함 */
    fun writeXcode(value: Any?): String = StringBuilder().also { write(it, value, 0) }.append('\n').toString()

    private fun write(out: StringBuilder, value: Any?, indent: Int) {
        when (value) {
            null -> out.append("null")
            is String -> quote(out, value)
            is Boolean, is Long, is Int -> out.append(value.toString())
            is Double -> out.append(if (value == Math.floor(value) && !value.isInfinite()) value.toLong().toString() + ".0" else value.toString())
            is Map<*, *> -> {
                if (value.isEmpty()) {
                    out.append("{}"); return
                }
                out.append("{\n")
                val keys = value.keys.map { it.toString() }.sorted()
                keys.forEachIndexed { i, key ->
                    pad(out, indent + 2); quote(out, key); out.append(" : ")
                    write(out, value[key], indent + 2)
                    if (i < keys.lastIndex) out.append(',')
                    out.append('\n')
                }
                pad(out, indent); out.append('}')
            }
            is List<*> -> {
                if (value.isEmpty()) {
                    out.append("[]"); return
                }
                out.append("[\n")
                value.forEachIndexed { i, item ->
                    pad(out, indent + 2); write(out, item, indent + 2)
                    if (i < value.lastIndex) out.append(',')
                    out.append('\n')
                }
                pad(out, indent); out.append(']')
            }
            else -> error("JSON 으로 쓸 수 없는 값: $value")
        }
    }

    private fun pad(out: StringBuilder, n: Int) = repeat(n) { out.append(' ') }

    private fun quote(out: StringBuilder, s: String) {
        out.append('"')
        for (c in s) {
            when (c) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                else -> if (c < ' ') out.append("\\u%04x".format(c.code)) else out.append(c)
            }
        }
        out.append('"')
    }

    private class Parser(val text: String) {
        var pos = 0

        fun skipWhitespace() {
            while (pos < text.length && text[pos].isWhitespace()) pos++
        }

        fun readValue(): Any? {
            skipWhitespace()
            require(pos < text.length) { "JSON 이 끝남" }
            return when (val c = text[pos]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) readNumber() else error("JSON 오류 (위치 $pos): '$c'")
            }
        }

        fun literal(word: String, value: Any?): Any? {
            require(text.startsWith(word, pos)) { "JSON 오류 (위치 $pos)" }
            pos += word.length
            return value
        }

        fun readObject(): Map<String, Any?> {
            val map = LinkedHashMap<String, Any?>()
            pos++ // {
            skipWhitespace()
            if (text[pos] == '}') {
                pos++; return map
            }
            while (true) {
                skipWhitespace()
                val key = readString()
                skipWhitespace()
                require(text[pos] == ':') { "':' 필요 (위치 $pos)" }
                pos++
                map[key] = readValue()
                skipWhitespace()
                when (text[pos++]) {
                    ',' -> continue
                    '}' -> return map
                    else -> error("',' 또는 '}' 필요 (위치 ${pos - 1})")
                }
            }
        }

        fun readArray(): List<Any?> {
            val list = ArrayList<Any?>()
            pos++ // [
            skipWhitespace()
            if (text[pos] == ']') {
                pos++; return list
            }
            while (true) {
                list += readValue()
                skipWhitespace()
                when (text[pos++]) {
                    ',' -> continue
                    ']' -> return list
                    else -> error("',' 또는 ']' 필요 (위치 ${pos - 1})")
                }
            }
        }

        fun readString(): String {
            require(text[pos] == '"') { "문자열 필요 (위치 $pos)" }
            pos++
            val sb = StringBuilder()
            while (true) {
                val c = text[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> when (val e = text[pos++]) {
                        '"', '\\', '/' -> sb.append(e)
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'u' -> {
                            sb.append(text.substring(pos, pos + 4).toInt(16).toChar()); pos += 4
                        }
                        else -> error("잘못된 escape \\$e (위치 $pos)")
                    }
                    else -> sb.append(c)
                }
            }
        }

        fun readNumber(): Any {
            val start = pos
            while (pos < text.length && (text[pos].isDigit() || text[pos] in "+-.eE")) pos++
            val lexeme = text.substring(start, pos)
            return if (lexeme.any { it in ".eE" }) lexeme.toDouble() else lexeme.toLong()
        }
    }
}
