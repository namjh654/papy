package cloud.elsa.app.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import cloud.elsa.app.R
import cloud.elsa.app.designsystem.theme.ElsaTheme

/** 알림 종류. 토스트·인라인 메시지가 같은 색/아이콘 규칙을 쓴다. */
enum class ElsaMessageType { Info, Success, Warning, Error }

internal data class ElsaMessageColors(
    val accent: Color,
    val container: Color,
    val onContainer: Color,
)

internal val ElsaMessageType.colors: ElsaMessageColors
    @Composable @ReadOnlyComposable
    get() {
        val c = ElsaTheme.colors
        return when (this) {
            ElsaMessageType.Info -> ElsaMessageColors(c.info, c.infoContainer, c.onInfoContainer)
            ElsaMessageType.Success -> ElsaMessageColors(c.success, c.successContainer, c.onSuccessContainer)
            ElsaMessageType.Warning -> ElsaMessageColors(c.warning, c.warningContainer, c.onWarningContainer)
            ElsaMessageType.Error -> ElsaMessageColors(c.error, c.errorContainer, c.onErrorContainer)
        }
    }

@get:DrawableRes
internal val ElsaMessageType.icon: Int
    get() = when (this) {
        ElsaMessageType.Info -> R.drawable.elsa_ic_info
        ElsaMessageType.Success -> R.drawable.elsa_ic_success
        ElsaMessageType.Warning -> R.drawable.elsa_ic_warning
        ElsaMessageType.Error -> R.drawable.elsa_ic_error
    }
