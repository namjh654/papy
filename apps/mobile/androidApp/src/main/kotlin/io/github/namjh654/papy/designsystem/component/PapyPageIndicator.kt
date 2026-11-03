package io.github.namjh654.papy.designsystem.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.namjh654.papy.designsystem.theme.PapyTheme
import io.github.namjh654.papy.designsystem.token.PapySpacing

/** 쪽 표시 점(온보딩·이미지 넘기기). 지금 쪽은 길고 진한 점. (iOS `PapyPageIndicator`) */
@Composable
fun PapyPageIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(PapySpacing.sm)) {
        repeat(pageCount) { index ->
            val selected = index == currentPage
            val width by animateDpAsState(if (selected) 20.dp else 8.dp, label = "indicator")
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(if (selected) PapyTheme.colors.primary else PapyTheme.colors.outline),
            )
        }
    }
}
