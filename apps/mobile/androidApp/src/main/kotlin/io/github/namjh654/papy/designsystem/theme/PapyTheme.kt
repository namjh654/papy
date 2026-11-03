package io.github.namjh654.papy.designsystem.theme

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
import io.github.namjh654.papy.designsystem.token.PapyColors
import io.github.namjh654.papy.designsystem.token.PapyDarkColors
import io.github.namjh654.papy.designsystem.token.PapyDefaultTypography
import io.github.namjh654.papy.designsystem.token.PapyLightColors
import io.github.namjh654.papy.designsystem.token.PapyRadius
import io.github.namjh654.papy.designsystem.token.PapyTypography

private val LocalPapyColors = staticCompositionLocalOf { PapyLightColors }
private val LocalPapyTypography = staticCompositionLocalOf { PapyDefaultTypography }

/**
 * 디자인 시스템 진입점. 화면과 컴포넌트는 `PapyTheme.colors` / `PapyTheme.typography` 와
 * 토큰 객체(`PapySpacing`, `PapyRadius`, `PapySize`)만 사용한다.
 *
 * Material3 컴포넌트(DatePicker, Dialog 등)도 같은 색을 쓰도록 ColorScheme 으로도 매핑한다.
 */
object PapyTheme {
    val colors: PapyColors
        @Composable @ReadOnlyComposable
        get() = LocalPapyColors.current

    val typography: PapyTypography
        @Composable @ReadOnlyComposable
        get() = LocalPapyTypography.current
}

@Composable
fun PapyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) PapyDarkColors else PapyLightColors
    val typography = PapyDefaultTypography

    CompositionLocalProvider(
        LocalPapyColors provides colors,
        LocalPapyTypography provides typography,
    ) {
        MaterialTheme(
            colorScheme = colors.toColorScheme(),
            typography = typography.toMaterialTypography(),
            shapes = PapyShapes,
            content = content,
        )
    }
}

private val PapyShapes = Shapes(
    extraSmall = RoundedCornerShape(PapyRadius.xs),
    small = RoundedCornerShape(PapyRadius.sm),
    medium = RoundedCornerShape(PapyRadius.md),
    large = RoundedCornerShape(PapyRadius.lg),
    extraLarge = RoundedCornerShape(PapyRadius.xl),
)

private fun PapyColors.toColorScheme(): ColorScheme {
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

private fun PapyTypography.toMaterialTypography(): Typography = Typography(
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
