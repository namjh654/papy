package io.github.namjh654.papy.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import io.github.namjh654.papy.R
import io.github.namjh654.papy.designsystem.theme.PapyTheme

/** 알림 종류. 토스트·인라인 메시지가 같은 색/아이콘 규칙을 쓴다. */
enum class PapyMessageType { Info, Success, Warning, Error }

internal data class PapyMessageColors(
    val accent: Color,
    val container: Color,
    val onContainer: Color,
)

internal val PapyMessageType.colors: PapyMessageColors
    @Composable @ReadOnlyComposable
    get() {
        val c = PapyTheme.colors
        return when (this) {
            PapyMessageType.Info -> PapyMessageColors(c.info, c.infoContainer, c.onInfoContainer)
            PapyMessageType.Success -> PapyMessageColors(c.success, c.successContainer, c.onSuccessContainer)
            PapyMessageType.Warning -> PapyMessageColors(c.warning, c.warningContainer, c.onWarningContainer)
            PapyMessageType.Error -> PapyMessageColors(c.error, c.errorContainer, c.onErrorContainer)
        }
    }

@get:DrawableRes
internal val PapyMessageType.icon: Int
    get() = when (this) {
        PapyMessageType.Info -> R.drawable.papy_ic_info
        PapyMessageType.Success -> R.drawable.papy_ic_success
        PapyMessageType.Warning -> R.drawable.papy_ic_warning
        PapyMessageType.Error -> R.drawable.papy_ic_error
    }
