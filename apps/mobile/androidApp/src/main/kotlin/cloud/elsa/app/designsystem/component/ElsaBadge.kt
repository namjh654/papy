package cloud.elsa.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import cloud.elsa.app.designsystem.theme.ElsaTheme
import cloud.elsa.app.designsystem.token.ElsaRadius
import cloud.elsa.app.designsystem.token.ElsaSpacing

/** 짧은 상태 표시(새 글·즐겨찾기 등). 색은 [ElsaMessageType] 을 따른다. (iOS `ElsaBadge`) */
@Composable
fun ElsaBadge(
    text: String,
    modifier: Modifier = Modifier,
    type: ElsaMessageType = ElsaMessageType.Info,
) {
    val colors = type.colors
    Text(
        text = text,
        style = ElsaTheme.typography.caption,
        color = colors.onContainer,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(ElsaRadius.xs))
            .background(colors.container)
            .padding(horizontal = ElsaSpacing.sm, vertical = ElsaSpacing.xxs),
    )
}
