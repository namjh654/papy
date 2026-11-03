package cloud.elsa.app.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cloud.elsa.app.designsystem.theme.ElsaTheme
import cloud.elsa.app.designsystem.token.ElsaRadius
import cloud.elsa.app.designsystem.token.ElsaSize
import cloud.elsa.app.designsystem.token.ElsaSpacing

enum class ElsaButtonStyle { Primary, Secondary, Outline, Text, Destructive }

enum class ElsaButtonSize { Large, Small }

/**
 * 공통 버튼. iOS `ElsaButton` 과 같은 style/size/isLoading 의미를 가진다.
 *
 * @param isLoading true 면 스피너를 표시하고 클릭을 무시한다(색은 유지).
 * @param fullWidth true 면 가로를 꽉 채운다.
 */
@Composable
fun ElsaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ElsaButtonStyle = ElsaButtonStyle.Primary,
    size: ElsaButtonSize = ElsaButtonSize.Large,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    fullWidth: Boolean = true,
    @DrawableRes leadingIcon: Int? = null,
) {
    val c = ElsaTheme.colors
    val (container, content) = when (style) {
        ElsaButtonStyle.Primary -> c.primary to c.onPrimary
        ElsaButtonStyle.Secondary -> c.fill to c.onSurface
        ElsaButtonStyle.Outline -> Color.Transparent to c.primary
        ElsaButtonStyle.Text -> Color.Transparent to c.primary
        ElsaButtonStyle.Destructive -> c.error to c.onError
    }
    val border = if (style == ElsaButtonStyle.Outline) {
        BorderStroke(ElsaSize.border, if (enabled) c.outlineStrong else c.outline)
    } else {
        null
    }
    val isTransparent = style == ElsaButtonStyle.Outline || style == ElsaButtonStyle.Text
    val height = if (size == ElsaButtonSize.Large) ElsaSize.controlHeight else ElsaSize.controlHeightSmall
    val horizontalPadding = if (size == ElsaButtonSize.Large) ElsaSpacing.xl else ElsaSpacing.lg

    Button(
        onClick = { if (!isLoading) onClick() },
        modifier = modifier
            .heightIn(min = height)
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier),
        enabled = enabled,
        shape = RoundedCornerShape(if (size == ElsaButtonSize.Large) ElsaRadius.lg else ElsaRadius.md),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = if (isTransparent) Color.Transparent else c.disabled,
            disabledContentColor = c.onDisabled,
        ),
        elevation = null,
        border = border,
        contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = ElsaSpacing.sm),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(ElsaSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(ElsaSize.iconSmall),
                    color = content,
                    strokeWidth = 2.dp,
                )
            } else if (leadingIcon != null) {
                Icon(
                    painter = painterResource(leadingIcon),
                    contentDescription = null,
                    modifier = Modifier.size(ElsaSize.icon),
                )
            }
            Text(
                text = text,
                style = if (size == ElsaButtonSize.Large) ElsaTheme.typography.button else ElsaTheme.typography.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
