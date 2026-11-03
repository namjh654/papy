package template.tooling

/**
 * design/tokens.json → Android(Compose) / iOS(SwiftUI) 토큰 코드 + Android 윈도우 테마 색 리소스.
 * design/fonts → Android res/font · iOS Resources/Fonts 복사(DEC-117).
 * design/icons/<이름>.svg → Android `papy_ic_<이름>` VectorDrawable · iOS 에셋 `papy_ic_<이름>`(DEC-118).
 * 생성물은 커밋한다. 생성 파일을 직접 고치지 말고 원본을 고친 뒤 `./gradlew generateDesignTokens`.
 */
class DesignTokens(private val repo: Repo) {

    /** 생성할 파일(저장소 상대 경로) → 내용 */
    fun outputs(): Map<String, String> {
        val tokens = Json.obj(Json.parse(repo.read(SOURCE)))
        return linkedMapOf(
            "${repo.androidSrc}/designsystem/token/PapyTokens.kt" to kotlin(tokens),
            "iosApp/iosApp/designsystem/token/papy.tokens.swift" to swift(tokens),
            "androidApp/src/main/res/values/papy_colors.xml" to androidColors(tokens, "light"),
            "androidApp/src/main/res/values-night/papy_colors.xml" to androidColors(tokens, "dark"),
        ).apply { putAll(icons()) }
    }

    /** 그대로 복사할 파일(글꼴): 생성 경로 → 원본 경로 */
    fun copies(): Map<String, String> {
        val font = Json.obj(Json.obj(Json.parse(repo.read(SOURCE)))["font"])
        return Json.obj(font["files"]).values.map { it as String }.flatMap { file ->
            val source = "$FONTS/$file.otf"
            listOf(
                "androidApp/src/main/res/font/${androidFontName(file)}.otf" to source,
                "$IOS_FONTS/$file.otf" to source,
            )
        }.toMap()
    }

    /** 생성물이 최신이 아닌 파일 목록 */
    fun stale(): List<String> {
        val text = outputs().filter { (path, content) ->
            val f = repo.file(path)
            !f.isFile || f.readText() != content
        }.keys
        val binary = copies().filter { (path, source) ->
            val f = repo.file(path)
            !f.isFile || !f.readBytes().contentEquals(repo.file(source).readBytes())
        }.keys
        return (text + binary).toList()
    }

    /** 바뀐 파일만 쓰고 쓴 목록을 돌려준다 */
    fun generate(): List<String> {
        val texts = outputs()
        val copies = copies()
        return stale().onEach { path ->
            texts[path]?.let { repo.write(path, it) }
                ?: repo.file(path).also { it.parentFile.mkdirs() }.writeBytes(repo.file(copies.getValue(path)).readBytes())
        }
    }

    /** design/icons/<이름>.svg → Android VectorDrawable + iOS 에셋(svg + Contents.json). 색은 화면에서 tint 로 정한다 */
    private fun icons(): Map<String, String> {
        val dir = repo.file(ICONS)
        val svgs = (dir.listFiles { f -> f.extension == "svg" } ?: emptyArray()).sortedBy { it.name }
        val out = linkedMapOf<String, String>()
        if (svgs.isEmpty()) return out
        out["$IOS_ICONS/Contents.json"] = """{
  "info" : {
    "author" : "xcode",
    "version" : 1
  }
}
"""
        svgs.forEach { svg ->
            val name = "papy_ic_${svg.nameWithoutExtension}"
            val icon = SvgIcon.parse(svg.readText())
            out["androidApp/src/main/res/drawable/$name.xml"] = icon.vectorDrawable("<!-- $ICON_HEADER -->")
            out["$IOS_ICONS/$name.imageset/$name.svg"] = icon.plainSvg()
            out["$IOS_ICONS/$name.imageset/Contents.json"] = """{
  "images" : [
    {
      "filename" : "$name.svg",
      "idiom" : "universal"
    }
  ],
  "info" : {
    "author" : "xcode",
    "version" : 1
  },
  "properties" : {
    "preserves-vector-representation" : true,
    "template-rendering-intent" : "template"
  }
}
"""
        }
        return out
    }

    private fun kotlin(t: Map<String, Any?>): String {
        val colors = Json.obj(t["color"])
        val out = mutableListOf("// $HEADER", "package ${repo.basePackage}.designsystem.token", "")
        out += listOf(
            "import androidx.compose.runtime.Immutable",
            "import androidx.compose.ui.graphics.Color",
            "import androidx.compose.ui.text.TextStyle",
            "import androidx.compose.ui.text.font.Font",
            "import androidx.compose.ui.text.font.FontFamily",
            "import androidx.compose.ui.text.font.FontWeight",
            "import androidx.compose.ui.text.style.LineBreak",

            "import androidx.compose.ui.unit.dp",
            "import androidx.compose.ui.unit.sp",
            "import ${repo.basePackage}.R",
            "",
            "/** 의미 기반(semantic) 색상. 화면·컴포넌트는 hex 대신 이 이름만 사용한다. */",
            "@Immutable",
            "data class PapyColors(",
        )
        colors.keys.forEach { out += "    val $it: Color," }
        out += listOf("    val isDark: Boolean,", ")", "")
        for ((mode, label) in listOf("light" to "Light", "dark" to "Dark")) {
            out += "val Papy${label}Colors = PapyColors("
            colors.forEach { (name, v) -> out += "    $name = ${kotlinColor(Json.obj(v)[mode] as String)}," }
            out += listOf("    isDark = ${mode == "dark"},", ")", "")
        }

        val font = Json.obj(t["font"])
        out += "/** ${font["family"]} (DEC-117) — design/fonts */"
        out += "val PapyFontFamily = FontFamily("
        Json.obj(font["files"]).forEach { (weight, file) ->
            out += "    Font(R.font.${androidFontName(file as String)}, ${KOTLIN_WEIGHT.getValue(weight)}),"
        }
        out += listOf(")", "")
        out += "/** 한국어를 글자가 아니라 어절 단위로 줄바꿈한다(Android 13+, 그 아래는 기본 동작) */"
        out += listOf("val PapyLineBreak = LineBreak.Paragraph.copy(wordBreak = LineBreak.WordBreak.Phrase)", "")

        val typography = Json.obj(t["typography"])
        out += listOf("@Immutable", "data class PapyTypography(")
        typography.keys.forEach { out += "    val $it: TextStyle," }
        out += listOf(")", "", "val PapyDefaultTypography = PapyTypography(")
        typography.forEach { (name, v) ->
            val s = Json.obj(v)
            out += "    $name = TextStyle(fontFamily = PapyFontFamily, fontSize = ${num(s["size"])}.sp, lineHeight = ${num(s["lineHeight"])}.sp, " +
                "fontWeight = ${KOTLIN_WEIGHT.getValue(s["weight"] as String)}, lineBreak = PapyLineBreak),"
        }
        out += listOf(")", "")

        for ((obj, key) in listOf("PapySpacing" to "spacing", "PapyRadius" to "radius", "PapySize" to "size")) {
            out += "object $obj {"
            Json.obj(t[key]).forEach { (name, v) -> out += "    val $name = ${num(v)}.dp" }
            out += listOf("}", "")
        }

        out += "/** 애니메이션/노출 시간(ms) */"
        out += "object PapyMotion {"
        Json.obj(t["motion"]).forEach { (name, v) -> out += "    const val $name = ${num(v)}" }
        out += listOf("}", "")
        return out.joinToString("\n")
    }

    private fun swift(t: Map<String, Any?>): String {
        val out = mutableListOf("// $HEADER", "", "import CoreText", "import SwiftUI", "import UIKit", "")
        out += listOf(
            "/// 의미 기반(semantic) 색상. light/dark 는 시스템 설정에 따라 자동 전환된다.",
            "enum PapyColor {",
        )
        Json.obj(t["color"]).forEach { (name, v) ->
            val c = Json.obj(v)
            out += "    static let $name = Color(papyLight: ${swiftColor(c["light"] as String)}, dark: ${swiftColor(c["dark"] as String)})"
        }
        out += listOf("}", "")

        for ((enum, key) in listOf("PapySpacing" to "spacing", "PapyRadius" to "radius", "PapySize" to "size")) {
            out += "enum $enum {"
            Json.obj(t[key]).forEach { (name, v) -> out += "    static let $name: CGFloat = ${num(v)}" }
            out += listOf("}", "")
        }

        out += "/// 애니메이션/노출 시간(초)"
        out += "enum PapyMotion {"
        Json.obj(t["motion"]).forEach { (name, v) -> out += "    static let $name: Double = ${((v as Number).toDouble() / 1000)}" }
        out += listOf("}", "")

        val font = Json.obj(t["font"])
        val files = Json.obj(font["files"])
        out += listOf(
            "struct PapyTextStyle {",
            "    let size: CGFloat",
            "    let lineHeight: CGFloat",
            "    let weight: Font.Weight",
            "    /// 굵기별 글꼴 파일의 PostScript 이름(${font["family"]}, DEC-117)",
            "    let fontName: String",
            "    /// 시스템 글자 크기(Dynamic Type)를 따를 때 기준 스타일 — 크기에서 정한다(DEC-128)",
            "    let relativeTo: Font.TextStyle",
            "",
            "    var font: Font { .custom(fontName, size: size, relativeTo: relativeTo) }",
            "}",
            "",
            "enum PapyFont {",
        )
        Json.obj(t["typography"]).forEach { (name, v) ->
            val s = Json.obj(v)
            val weight = s["weight"] as String
            out += "    static let $name = PapyTextStyle(size: ${num(s["size"])}, lineHeight: ${num(s["lineHeight"])}, " +
                "weight: ${SWIFT_WEIGHT.getValue(weight)}, fontName: \"${files.getValue(weight)}\", " +
                "relativeTo: ${swiftTextStyle((s["size"] as Number).toDouble())})"
        }
        out += listOf(
            "",
            "    /// 앱 번들의 글꼴을 등록한다. 앱 시작 때 한 번(Info.plist 수정 없이)",
            "    static func registerFonts() {",
            "        for name in [${files.values.joinToString(", ") { "\"$it\"" }}] {",
            "            guard let url = Bundle.main.url(forResource: name, withExtension: \"otf\") else { continue }",
            "            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)",
            "        }",
            "    }",
            "}",
            "",
        )

        out += listOf(
            "extension Color {",
            "    /// 0xRRGGBBAA 값 두 개로 light/dark 동적 색을 만든다.",
            "    init(papyLight light: UInt32, dark: UInt32) {",
            "        self.init(uiColor: UIColor { traits in",
            "            UIColor(papyRGBA: traits.userInterfaceStyle == .dark ? dark : light)",
            "        })",
            "    }",
            "}",
            "",
            "extension UIColor {",
            "    convenience init(papyRGBA value: UInt32) {",
            "        self.init(",
            "            red: CGFloat((value >> 24) & 0xFF) / 255,",
            "            green: CGFloat((value >> 16) & 0xFF) / 255,",
            "            blue: CGFloat((value >> 8) & 0xFF) / 255,",
            "            alpha: CGFloat(value & 0xFF) / 255",
            "        )",
            "    }",
            "}",
            "",
        )
        return out.joinToString("\n")
    }

    // 윈도우 테마는 일부 색만 쓰지만 전체를 내보낸다(lint UnusedResources 무시).
    private fun androidColors(t: Map<String, Any?>, mode: String): String {
        val out = mutableListOf(
            """<?xml version="1.0" encoding="utf-8"?>""",
            "<!-- $HEADER -->",
            """<resources xmlns:tools="http://schemas.android.com/tools" tools:ignore="UnusedResources">""",
        )
        Json.obj(t["color"]).forEach { (name, v) ->
            val (rgb, a) = parseColor(Json.obj(v)[mode] as String)
            out += """    <color name="papy_${snake(name)}">#${hex2(a)}$rgb</color>"""
        }
        out += listOf("</resources>", "")
        return out.joinToString("\n")
    }

    companion object {
        const val SOURCE = "design/tokens.json"
        const val FONTS = "design/fonts"
        const val ICONS = "design/icons"
        const val IOS_FONTS = "iosApp/iosApp/Resources/Fonts"
        const val IOS_ICONS = "iosApp/iosApp/Assets.xcassets/Icons"
        const val ICON_HEADER = "자동 생성 파일 — 수정 금지. 원본: design/icons (Lucide, ISC) · 생성: ./gradlew generateDesignTokens"

        /** Android 리소스 이름 규칙(소문자·밑줄): Pretendard-SemiBold → pretendard_semibold */
        fun androidFontName(file: String): String = file.lowercase().replace('-', '_')
        const val HEADER = "자동 생성 파일 — 수정 금지. 원본: design/tokens.json · 생성: ./gradlew generateDesignTokens"

        private val KOTLIN_WEIGHT = mapOf(
            "regular" to "FontWeight.Normal",
            "medium" to "FontWeight.Medium",
            "semibold" to "FontWeight.SemiBold",
            "bold" to "FontWeight.Bold",
        )
        private val SWIFT_WEIGHT = mapOf("regular" to ".regular", "medium" to ".medium", "semibold" to ".semibold", "bold" to ".bold")

        /** #RRGGBB / #RRGGBBAA → (RRGGBB, alpha 0-255) */
        fun parseColor(value: String): Pair<String, Int> {
            val m = Regex("#([0-9A-Fa-f]{6})([0-9A-Fa-f]{2})?").matchEntire(value) ?: error("잘못된 색 값: $value")
            return m.groupValues[1].uppercase() to (m.groupValues[2].ifEmpty { "FF" }).toInt(16)
        }

        private fun hex2(v: Int) = "%02X".format(v)

        fun kotlinColor(value: String): String = parseColor(value).let { (rgb, a) -> "Color(0x${hex2(a)}$rgb)" }

        fun swiftColor(value: String): String = parseColor(value).let { (rgb, a) -> "0x$rgb${hex2(a)}" }

        /** 정수 값이면 소수점 없이 */
        fun num(v: Any?): String = when (v) {
            is Long -> v.toString()
            is Double -> if (v == Math.floor(v)) v.toLong().toString() else v.toString()
            else -> error("숫자가 아님: $v")
        }

        fun snake(name: String): String = name.replace(Regex("(?<!^)(?=[A-Z])"), "_").lowercase()
    }
}

/** 글자 크기 → iOS Dynamic Type 기준 스타일(늘어나는 비율이 비슷한 시스템 스타일) */
private fun swiftTextStyle(size: Double): String = when {
    size >= 28 -> ".largeTitle"
    size >= 24 -> ".title"
    size >= 20 -> ".title2"
    size >= 16 -> ".body"
    size >= 14 -> ".subheadline"
    else -> ".caption"
}
