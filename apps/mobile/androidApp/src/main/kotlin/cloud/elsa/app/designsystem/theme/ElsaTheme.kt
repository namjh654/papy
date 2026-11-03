package cloud.elsa.app.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import cloud.elsa.app.designsystem.token.ElsaColors
import cloud.elsa.app.designsystem.token.ElsaDarkColors
import cloud.elsa.app.designsystem.token.ElsaDefaultTypography
import cloud.elsa.app.designsystem.token.ElsaLightColors
import cloud.elsa.app.designsystem.token.ElsaRadius
import cloud.elsa.app.designsystem.token.ElsaTypography

private val LocalElsaColors = staticCompositionLocalOf { ElsaLightColors }
private val LocalElsaTypography = staticCompositionLocalOf { ElsaDefaultTypography }

/**
 * 디자인 시스템 진입점. 화면과 컴포넌트는 `ElsaTheme.colors` / `ElsaTheme.typography` 와
 * 토큰 객체(`ElsaSpacing`, `ElsaRadius`, `ElsaSize`)만 사용한다.
 *
 * Material3 컴포넌트(DatePicker, Dialog 등)도 같은 색을 쓰도록 ColorScheme 으로도 매핑한다.
 */
object ElsaTheme {
    val colors: ElsaColors
        @Composable @ReadOnlyComposable
        get() = LocalElsaColors.current

    val typography: ElsaTypography
        @Composable @ReadOnlyComposable
        get() = LocalElsaTypography.current
}

@Composable
fun ElsaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) ElsaDarkColors else ElsaLightColors
    val typography = ElsaDefaultTypography

    CompositionLocalProvider(
        LocalElsaColors provides colors,
        LocalElsaTypography provides typography,
    ) {
        MaterialTheme(
            colorScheme = colors.toColorScheme(),
            typography = typography.toMaterialTypography(),
            shapes = ElsaShapes,
            content = content,
        )
    }
}

private val ElsaShapes = Shapes(
    extraSmall = RoundedCornerShape(ElsaRadius.xs),
    small = RoundedCornerShape(ElsaRadius.sm),
    medium = RoundedCornerShape(ElsaRadius.md),
    large = RoundedCornerShape(ElsaRadius.lg),
    extraLarge = RoundedCornerShape(ElsaRadius.xl),
)

private fun ElsaColors.toColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = info,
        onTertiary = onInfo,
        tertiaryContainer = infoContainer,
        onTertiaryContainer = onInfoContainer,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceTint = primary,
        outline = outlineStrong,
        outlineVariant = outline,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
        scrim = scrim,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = surfaceVariant,
    )
}

private fun ElsaTypography.toMaterialTypography(): Typography = Typography(
    displaySmall = display,
    headlineMedium = headline,
    headlineSmall = headline,
    titleLarge = title,
    titleMedium = subtitle,
    titleSmall = label,
    bodyLarge = body,
    bodyMedium = bodySmall,
    bodySmall = caption,
    labelLarge = button,
    labelMedium = label,
    labelSmall = caption,
)
