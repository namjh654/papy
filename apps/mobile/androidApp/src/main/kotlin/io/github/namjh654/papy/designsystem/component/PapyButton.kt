package io.github.namjh654.papy.designsystem.component

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
import io.github.namjh654.papy.designsystem.theme.PapyTheme
import io.github.namjh654.papy.designsystem.token.PapyRadius
import io.github.namjh654.papy.designsystem.token.PapySize
import io.github.namjh654.papy.designsystem.token.PapySpacing

enum class PapyButtonStyle { Primary, Secondary, Outline, Text, Destructive }

enum class PapyButtonSize { Large, Small }

/**
 * 공통 버튼. iOS `PapyButton` 과 같은 style/size/isLoading 의미를 가진다.
 *
 * @param isLoading true 면 스피너를 표시하고 클릭을 무시한다(색은 유지).
 * @param fullWidth true 면 가로를 꽉 채운다.
 */
@Composable
fun PapyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PapyButtonStyle = PapyButtonStyle.Primary,
    size: PapyButtonSize = PapyButtonSize.Large,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    fullWidth: Boolean = true,
    @DrawableRes leadingIcon: Int? = null,
) {
    val c = PapyTheme.colors
    val (container, content) = when (style) {
        PapyButtonStyle.Primary -> c.primary to c.onPrimary
        PapyButtonStyle.Secondary -> c.fill to c.onSurface
        PapyButtonStyle.Outline -> Color.Transparent to c.primary
        PapyButtonStyle.Text -> Color.Transparent to c.primary
        PapyButtonStyle.Destructive -> c.error to c.onError
    }
    val border = if (style == PapyButtonStyle.Outline) {
        BorderStroke(PapySize.border, if (enabled) c.outlineStrong else c.outline)
    } else {
        null
    }
    val isTransparent = style == PapyButtonStyle.Outline || style == PapyButtonStyle.Text
    val height = if (size == PapyButtonSize.Large) PapySize.controlHeight else PapySize.controlHeightSmall
    val horizontalPadding = if (size == PapyButtonSize.Large) PapySpacing.xl else PapySpacing.lg

    Button(
        onClick = { if (!isLoading) onClick() },
        modifier = modifier
            .heightIn(min = height)
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier),
        enabled = enabled,
        shape = RoundedCornerShape(if (size == PapyButtonSize.Large) PapyRadius.lg else PapyRadius.md),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = if (isTransparent) Color.Transparent else c.disabled,
            disabledContentColor = c.onDisabled,
        ),
        elevation = null,
        border = border,
        contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = PapySpacing.sm),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(PapySpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(PapySize.iconSmall),
                    color = content,
                    strokeWidth = 2.dp,
                )
            } else if (leadingIcon != null) {
                Icon(
                    painter = painterResource(leadingIcon),
                    contentDescription = null,
                    modifier = Modifier.size(PapySize.icon),
                )
            }
            Text(
                text = text,
                style = if (size == PapyButtonSize.Large) PapyTheme.typography.button else PapyTheme.typography.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
