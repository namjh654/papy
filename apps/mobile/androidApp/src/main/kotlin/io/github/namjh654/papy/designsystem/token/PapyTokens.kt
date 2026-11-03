// 자동 생성 파일 — 수정 금지. 원본: design/tokens.json · 생성: ./gradlew generateDesignTokens
package io.github.namjh654.papy.designsystem.token

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.namjh654.papy.R

/** 의미 기반(semantic) 색상. 화면·컴포넌트는 hex 대신 이 이름만 사용한다. */
@Immutable
data class PapyColors(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val background: Color,
    val onBackground: Color,
    val backgroundGrouped: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val onSurfaceTertiary: Color,
    val fill: Color,
    val fillPressed: Color,
    val divider: Color,
    val outline: Color,
    val outlineStrong: Color,
    val disabled: Color,
    val onDisabled: Color,
    val scrim: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val info: Color,
    val onInfo: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
    val kakao: Color,
    val onKakao: Color,
    val naver: Color,
    val onNaver: Color,
    val google: Color,
    val onGoogle: Color,
    val googleOutline: Color,
    val apple: Color,
    val onApple: Color,
    val isDark: Boolean,
)

val PapyLightColors = PapyColors(
    primary = Color(0xFF3452DB),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFECF0FF),
    onPrimaryContainer = Color(0xFF1A2C8F),
    secondary = Color(0xFF4E5566),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF1F2F5),
    onSecondaryContainer = Color(0xFF333844),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF191B20),
    backgroundGrouped = Color(0xFFF3F4F7),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191B20),
    surfaceVariant = Color(0xFFF3F4F7),
    onSurfaceVariant = Color(0xFF6A7180),
    onSurfaceTertiary = Color(0xFF9BA1AC),
    fill = Color(0xFFF2F3F6),
    fillPressed = Color(0xFFE6E8EC),
    divider = Color(0xFFEEEFF2),
    outline = Color(0xFFDDE0E6),
    outlineStrong = Color(0xFF9197A3),
    disabled = Color(0xFFEEF0F3),
    onDisabled = Color(0xFFA7ACB6),
    scrim = Color(0x66000000),
    error = Color(0xFFD92D20),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE4E2),
    onErrorContainer = Color(0xFF7A1A12),
    success = Color(0xFF12A150),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFDCFCE7),
    onSuccessContainer = Color(0xFF0B5130),
    warning = Color(0xFFB54708),
    onWarning = Color(0xFFFFFFFF),
    warningContainer = Color(0xFFFEF0C7),
    onWarningContainer = Color(0xFF7A2E0E),
    info = Color(0xFF1570EF),
    onInfo = Color(0xFFFFFFFF),
    infoContainer = Color(0xFFD1E9FF),
    onInfoContainer = Color(0xFF194185),
    kakao = Color(0xFFFEE500),
    onKakao = Color(0xD9000000),
    naver = Color(0xFF03C75A),
    onNaver = Color(0xFFFFFFFF),
    google = Color(0xFFFFFFFF),
    onGoogle = Color(0xFF1F1F1F),
    googleOutline = Color(0xFF747775),
    apple = Color(0xFF000000),
    onApple = Color(0xFFFFFFFF),
    isDark = false,
)

val PapyDarkColors = PapyColors(
    primary = Color(0xFF8FA4FF),
    onPrimary = Color(0xFF0E1B63),
    primaryContainer = Color(0xFF1F2E7A),
    onPrimaryContainer = Color(0xFFDCE3FF),
    secondary = Color(0xFFC3C8D4),
    onSecondary = Color(0xFF22262F),
    secondaryContainer = Color(0xFF2A2D35),
    onSecondaryContainer = Color(0xFFE4E6EC),
    background = Color(0xFF121317),
    onBackground = Color(0xFFECEDF1),
    backgroundGrouped = Color(0xFF0B0C0F),
    surface = Color(0xFF1C1D22),
    onSurface = Color(0xFFECEDF1),
    surfaceVariant = Color(0xFF25272D),
    onSurfaceVariant = Color(0xFFA3A9B6),
    onSurfaceTertiary = Color(0xFF747A87),
    fill = Color(0xFF26282E),
    fillPressed = Color(0xFF30323A),
    divider = Color(0xFF2A2C33),
    outline = Color(0xFF363943),
    outlineStrong = Color(0xFF6E7482),
    disabled = Color(0xFF2A2C32),
    onDisabled = Color(0xFF5F6470),
    scrim = Color(0xB3000000),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF5C0B05),
    errorContainer = Color(0xFF7A1A12),
    onErrorContainer = Color(0xFFFEE4E2),
    success = Color(0xFF4ADE80),
    onSuccess = Color(0xFF053B1F),
    successContainer = Color(0xFF0B5130),
    onSuccessContainer = Color(0xFFDCFCE7),
    warning = Color(0xFFFDB022),
    onWarning = Color(0xFF4E1D09),
    warningContainer = Color(0xFF7A2E0E),
    onWarningContainer = Color(0xFFFEF0C7),
    info = Color(0xFF84CAFF),
    onInfo = Color(0xFF0B2A5B),
    infoContainer = Color(0xFF194185),
    onInfoContainer = Color(0xFFD1E9FF),
    kakao = Color(0xFFFEE500),
    onKakao = Color(0xD9000000),
    naver = Color(0xFF03C75A),
    onNaver = Color(0xFFFFFFFF),
    google = Color(0xFF131314),
    onGoogle = Color(0xFFE3E3E3),
    googleOutline = Color(0xFF8E918F),
    apple = Color(0xFFFFFFFF),
    onApple = Color(0xFF000000),
    isDark = true,
)

/** Pretendard (DEC-117) — design/fonts */
val PapyFontFamily = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
)

/** 한국어를 글자가 아니라 어절 단위로 줄바꿈한다(Android 13+, 그 아래는 기본 동작) */
val PapyLineBreak = LineBreak.Paragraph.copy(wordBreak = LineBreak.WordBreak.Phrase)

@Immutable
data class PapyTypography(
    val display: TextStyle,
    val headline: TextStyle,
    val title: TextStyle,
    val subtitle: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val bodySmall: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    val button: TextStyle,
)

val PapyDefaultTypography = PapyTypography(
    display = TextStyle(fontFamily = PapyFontFamily, fontSize = 30.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, lineBreak = PapyLineBreak),
    headline = TextStyle(fontFamily = PapyFontFamily, fontSize = 26.sp, lineHeight = 35.sp, fontWeight = FontWeight.Bold, lineBreak = PapyLineBreak),
    title = TextStyle(fontFamily = PapyFontFamily, fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, lineBreak = PapyLineBreak),
    subtitle = TextStyle(fontFamily = PapyFontFamily, fontSize = 17.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold, lineBreak = PapyLineBreak),
    body = TextStyle(fontFamily = PapyFontFamily, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal, lineBreak = PapyLineBreak),
    bodyStrong = TextStyle(fontFamily = PapyFontFamily, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, lineBreak = PapyLineBreak),
    bodySmall = TextStyle(fontFamily = PapyFontFamily, fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal, lineBreak = PapyLineBreak),
    label = TextStyle(fontFamily = PapyFontFamily, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, lineBreak = PapyLineBreak),
    caption = TextStyle(fontFamily = PapyFontFamily, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal, lineBreak = PapyLineBreak),
    button = TextStyle(fontFamily = PapyFontFamily, fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, lineBreak = PapyLineBreak),
)

object PapySpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
    val section = 40.dp
}

object PapyRadius {
    val xs = 6.dp
    val sm = 10.dp
    val md = 14.dp
    val lg = 18.dp
    val xl = 24.dp
    val full = 999.dp
}

object PapySize {
    val controlHeight = 56.dp
    val controlHeightSmall = 40.dp
    val checkbox = 22.dp
    val icon = 20.dp
    val iconSmall = 16.dp
    val iconLarge = 24.dp
    val border = 1.dp
    val borderFocused = 2.dp
    val screenPadding = 24.dp
    val imageThumbnail = 88.dp
    val listRowHeight = 64.dp
    val topBarHeight = 56.dp
}

/** 애니메이션/노출 시간(ms) */
object PapyMotion {
    const val fast = 150
    const val normal = 250
    const val toast = 2500
}
