package template.tooling

/**
 * 선(stroke) 아이콘 SVG(Lucide 형식: 24×24, stroke 2, round) 를 플랫폼 형식으로 바꾼다(DEC-118). 라이브러리 없이
 * path · circle · line · rect · polyline · polygon 만 다룬다 — 다른 요소가 있으면 실패해서 빠뜨리지 않는다.
 * 채운 아이콘(선택된 좋아요 등)은 같은 SVG 에 `fill="currentColor"` 를 준다 → 선과 같은 색으로 안을 채운다.
 */
class SvgIcon private constructor(
    private val viewBox: List<Double>,
    private val strokeWidth: String,
    private val lineCap: String,
    private val lineJoin: String,
    private val paths: List<String>,
    private val filled: Boolean,
) {

    /** Android VectorDrawable. 색은 화면에서 tint 로 정하므로 검은 선으로 둔다 */
    fun vectorDrawable(header: String): String {
        val out = mutableListOf(
            """<?xml version="1.0" encoding="utf-8"?>""",
            header,
            """<vector xmlns:android="http://schemas.android.com/apk/res/android"""",
            """    android:width="${num(viewBox[2])}dp"""",
            """    android:height="${num(viewBox[3])}dp"""",
            """    android:viewportWidth="${num(viewBox[2])}"""",
            """    android:viewportHeight="${num(viewBox[3])}">""",
        )
        paths.forEach { d ->
            out += listOf(
                "    <path",
                """        android:pathData="$d"""",
                """        android:fillColor="${if (filled) "#FF000000" else "#00000000"}"""",
                """        android:strokeColor="#FF000000"""",
                """        android:strokeWidth="$strokeWidth"""",
                """        android:strokeLineCap="$lineCap"""",
                """        android:strokeLineJoin="$lineJoin" />""",
            )
        }
        out += listOf("</vector>", "")
        return out.joinToString("\n")
    }

    /** iOS 에셋용 SVG. 템플릿 이미지는 알파로 색을 입히므로 currentColor 대신 검은 선 */
    fun plainSvg(): String {
        val out = mutableListOf(
            """<svg xmlns="http://www.w3.org/2000/svg" width="${num(viewBox[2])}" height="${num(viewBox[3])}" """ +
                """viewBox="${viewBox.joinToString(" ") { num(it) }}" fill="${if (filled) "#000000" else "none"}" stroke="#000000" """ +
                """stroke-width="$strokeWidth" stroke-linecap="$lineCap" stroke-linejoin="$lineJoin">""",
        )
        paths.forEach { out += """  <path d="$it"/>""" }
        out += listOf("</svg>", "")
        return out.joinToString("\n")
    }

    companion object {
        private val ELEMENT = Regex("""<([a-z]+)\b([^>]*?)/?>""", RegexOption.DOT_MATCHES_ALL)
        // 이름에 숫자가 들어간다(x1·y2) — 빠뜨리면 선이 0 길이가 된다
        private val ATTRIBUTE = Regex("""([a-zA-Z][a-zA-Z0-9:-]*)\s*=\s*"([^"]*)"""")

        fun parse(text: String): SvgIcon {
            val body = text.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
            var root: Map<String, String>? = null
            val paths = mutableListOf<String>()
            ELEMENT.findAll(body).forEach { m ->
                val attrs = ATTRIBUTE.findAll(m.groupValues[2]).associate { it.groupValues[1] to it.groupValues[2] }
                fun d(name: String) = attrs[name]?.toDouble() ?: 0.0
                when (val tag = m.groupValues[1]) {
                    "svg" -> root = attrs
                    "path" -> paths += attrs.getValue("d")
                    "circle" -> {
                        val (cx, cy, r) = Triple(d("cx"), d("cy"), d("r"))
                        paths += "M${num(cx - r)},${num(cy)}a${num(r)},${num(r)} 0 1,0 ${num(2 * r)},0a${num(r)},${num(r)} 0 1,0 ${num(-2 * r)},0"
                    }
                    "line" -> paths += "M${num(d("x1"))},${num(d("y1"))}L${num(d("x2"))},${num(d("y2"))}"
                    "rect" -> paths += rect(d("x"), d("y"), d("width"), d("height"), attrs["rx"]?.toDouble() ?: attrs["ry"]?.toDouble() ?: 0.0)
                    "polyline", "polygon" -> {
                        val points = attrs.getValue("points").trim().split(Regex("[\\s,]+")).map { it.toDouble() }.chunked(2)
                        paths += "M" + points.joinToString("L") { (x, y) -> "${num(x)},${num(y)}" } + if (tag == "polygon") "Z" else ""
                    }
                    else -> error("지원하지 않는 SVG 요소: <$tag>")
                }
            }
            val svg = root ?: error("<svg> 가 없음")
            require(svg["stroke"] == "currentColor" && svg["fill"] in setOf("none", "currentColor")) {
                "선 아이콘(stroke=currentColor, fill=none 또는 currentColor)만 지원"
            }
            return SvgIcon(
                viewBox = (svg["viewBox"] ?: "0 0 24 24").trim().split(Regex("\\s+")).map { it.toDouble() },
                strokeWidth = svg["stroke-width"] ?: "2",
                lineCap = svg["stroke-linecap"] ?: "round",
                lineJoin = svg["stroke-linejoin"] ?: "round",
                paths = paths,
                filled = svg["fill"] == "currentColor",
            )
        }

        private fun rect(x: Double, y: Double, w: Double, h: Double, rx: Double): String {
            if (rx <= 0.0) return "M${num(x)},${num(y)}h${num(w)}v${num(h)}h${num(-w)}Z"
            val r = minOf(rx, w / 2, h / 2)
            return "M${num(x + r)},${num(y)}h${num(w - 2 * r)}a${num(r)},${num(r)} 0 0 1 ${num(r)},${num(r)}" +
                "v${num(h - 2 * r)}a${num(r)},${num(r)} 0 0 1 ${num(-r)},${num(r)}" +
                "h${num(-(w - 2 * r))}a${num(r)},${num(r)} 0 0 1 ${num(-r)},${num(-r)}" +
                "v${num(-(h - 2 * r))}a${num(r)},${num(r)} 0 0 1 ${num(r)},${num(-r)}Z"
        }

        private fun num(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else v.toString()
    }
}
