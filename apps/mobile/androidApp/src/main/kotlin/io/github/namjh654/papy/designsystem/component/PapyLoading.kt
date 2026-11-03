package io.github.namjh654.papy.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.namjh654.papy.designsystem.theme.PapyTheme
import io.github.namjh654.papy.designsystem.token.PapyRadius
import io.github.namjh654.papy.designsystem.token.PapySpacing

@Composable
fun PapyLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    CircularProgressIndicator(
        modifier = modifier.size(size),
        color = PapyTheme.colors.primary,
        trackColor = PapyTheme.colors.surfaceVariant,
        strokeWidth = 3.dp,
    )
}

/**
 * 화면 전체를 덮는 로딩. 표시 중에는 아래 화면 터치를 막는다. 화면 최상단(Box 마지막 자식)에 둔다.
 */
@Composable
fun PapyLoadingOverlay(
    isLoading: Boolean,
    message: String? = null,
) {
    if (!isLoading) return
    val c = PapyTheme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = RoundedCornerShape(PapyRadius.lg), color = c.surface) {
            Column(
                modifier = Modifier.padding(PapySpacing.xl),
                verticalArrangement = Arrangement.spacedBy(PapySpacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PapyLoadingIndicator()
                message?.let {
                    Text(text = it, style = PapyTheme.typography.bodySmall, color = c.onSurfaceVariant)
                }
            }
        }
    }
}
