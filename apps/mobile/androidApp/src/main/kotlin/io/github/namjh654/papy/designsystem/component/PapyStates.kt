package io.github.namjh654.papy.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import io.github.namjh654.papy.R
import io.github.namjh654.papy.designsystem.theme.PapyTheme
import io.github.namjh654.papy.designsystem.token.PapySize
import io.github.namjh654.papy.designsystem.token.PapySpacing

/** 빈 화면: 가운데 정렬 문구(+ 선택 액션). 목록이 비었을 때 PapyPagedList 가 쓴다 */
@Composable
fun PapyEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = PapySpacing.section),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PapySpacing.sm),
    ) {
        Text(text = title, style = PapyTheme.typography.subtitle, color = PapyTheme.colors.onSurface, textAlign = TextAlign.Center)
        message?.let {
            Text(text = it, style = PapyTheme.typography.body, color = PapyTheme.colors.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        if (actionText != null && onAction != null) {
            PapyButton(text = actionText, onClick = onAction, style = PapyButtonStyle.Secondary, size = PapyButtonSize.Small, fullWidth = false)
        }
    }
}

/** 전체 오류 화면: 오류 문구 + 다시 시도. 보여줄 내용이 없을 때만 쓴다(내용이 있으면 PapyInlineMessage) */
@Composable
fun PapyErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = PapySpacing.section),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PapySpacing.md),
    ) {
        Icon(
            painter = painterResource(R.drawable.papy_ic_error),
            contentDescription = null,
            tint = PapyTheme.colors.onSurfaceTertiary,
            modifier = Modifier.size(PapySize.icon * 2),
        )
        Text(text = message, style = PapyTheme.typography.body, color = PapyTheme.colors.onSurfaceVariant, textAlign = TextAlign.Center)
        PapyButton(
            text = stringResource(R.string.common_retry),
            onClick = onRetry,
            style = PapyButtonStyle.Secondary,
            size = PapyButtonSize.Small,
            fullWidth = false,
        )
    }
}

/** 오프라인 안내 띠. 루트(AndroidNavHost)가 NetworkMonitor.isOnline 을 보고 화면 위에 한 번만 둔다 */
@Composable
fun PapyOfflineBanner(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(visible = visible, enter = expandVertically(), exit = shrinkVertically()) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(PapyTheme.colors.onSurface)
                .padding(horizontal = PapySpacing.lg, vertical = PapySpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(PapySpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.papy_ic_offline),
                contentDescription = null,
                tint = PapyTheme.colors.surface,
                modifier = Modifier.size(PapySize.icon),
            )
            Text(text = stringResource(R.string.offline_banner), style = PapyTheme.typography.bodySmall, color = PapyTheme.colors.surface)
        }
    }
}
