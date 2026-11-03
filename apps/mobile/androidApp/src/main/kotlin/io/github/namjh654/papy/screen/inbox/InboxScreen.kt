package io.github.namjh654.papy.screen.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.namjh654.papy.R
import io.github.namjh654.papy.component.EventEffect
import io.github.namjh654.papy.core.common.time.RelativeTime
import io.github.namjh654.papy.designsystem.component.PapyButton
import io.github.namjh654.papy.designsystem.component.PapyButtonSize
import io.github.namjh654.papy.designsystem.component.PapyButtonStyle
import io.github.namjh654.papy.designsystem.component.PapyCard
import io.github.namjh654.papy.designsystem.component.PapyPagedList
import io.github.namjh654.papy.designsystem.component.PapyScaffold
import io.github.namjh654.papy.designsystem.component.PapyScreenHeader
import io.github.namjh654.papy.designsystem.format.label
import io.github.namjh654.papy.designsystem.theme.PapyTheme
import io.github.namjh654.papy.designsystem.token.PapySpacing
import io.github.namjh654.papy.feature.inbox.InboxEvent
import io.github.namjh654.papy.feature.inbox.InboxViewModel
import org.koin.androidx.compose.koinViewModel

/**
 * 알림함: 안 읽은 알림은 점 + 진한 글씨, 오른쪽 위 "모두 읽음".
 * @param onOpenLink 알림에 연결된 화면 열기(딥링크와 같은 형식)
 */
@Composable
fun InboxScreen(
    onOpenLink: (String) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val viewModel: InboxViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    EventEffect(viewModel.events) { event ->
        when (event) {
            is InboxEvent.Open -> onOpenLink(event.link)
        }
    }

    PapyScaffold(
        onBack = onBack,
        containerColor = PapyTheme.colors.backgroundGrouped,
        actions = {
            if (state.hasUnread) {
                PapyButton(
                    text = stringResource(R.string.inbox_mark_all_read),
                    onClick = viewModel::markAllRead,
                    style = PapyButtonStyle.Text,
                    size = PapyButtonSize.Small,
                    fullWidth = false,
                )
            }
        },
    ) { padding ->
        PapyPagedList(
            state = state.list,
            key = { it.id },
            emptyTitle = stringResource(R.string.inbox_empty),
            onRefresh = viewModel::refresh,
            onLoadMore = viewModel::loadMore,
            onRetry = viewModel::load,
            onRetryLoadMore = viewModel::retryLoadMore,
            modifier = Modifier.padding(padding),
            header = {
                item(key = "header") { PapyScreenHeader(title = stringResource(R.string.inbox_title)) }
            },
        ) { item ->
            PapyCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { viewModel.itemClicked(item) },
                verticalArrangement = Arrangement.spacedBy(PapySpacing.xxs),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PapySpacing.sm)) {
                    if (!item.read) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(PapyTheme.colors.primary))
                    }
                    Text(
                        text = item.title,
                        style = if (item.read) PapyTheme.typography.body else PapyTheme.typography.bodyStrong,
                        color = if (item.read) PapyTheme.colors.onSurfaceVariant else PapyTheme.colors.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = RelativeTime.of(item.createdAt).label(),
                        style = PapyTheme.typography.caption,
                        color = PapyTheme.colors.onSurfaceTertiary,
                    )
                }
                item.body?.let {
                    Text(text = it, style = PapyTheme.typography.bodySmall, color = PapyTheme.colors.onSurfaceVariant)
                }
            }
        }
    }
}
