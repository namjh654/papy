package io.github.namjh654.papy.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import io.github.namjh654.papy.designsystem.theme.PapyTheme
import io.github.namjh654.papy.designsystem.token.PapyRadius
import io.github.namjh654.papy.designsystem.token.PapySpacing

/** 짧은 상태 표시(새 글·즐겨찾기 등). 색은 [PapyMessageType] 을 따른다. (iOS `PapyBadge`) */
@Composable
fun PapyBadge(
    text: String,
    modifier: Modifier = Modifier,
    type: PapyMessageType = PapyMessageType.Info,
) {
    val colors = type.colors
    Text(
        text = text,
        style = PapyTheme.typography.caption,
        color = colors.onContainer,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(PapyRadius.xs))
            .background(colors.container)
            .padding(horizontal = PapySpacing.sm, vertical = PapySpacing.xxs),
    )
}
