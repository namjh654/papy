package cloud.elsa.app.screen.inbox

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
import cloud.elsa.app.R
import cloud.elsa.app.component.EventEffect
import cloud.elsa.app.core.common.time.RelativeTime
import cloud.elsa.app.designsystem.component.ElsaButton
import cloud.elsa.app.designsystem.component.ElsaButtonSize
import cloud.elsa.app.designsystem.component.ElsaButtonStyle
import cloud.elsa.app.designsystem.component.ElsaCard
import cloud.elsa.app.designsystem.component.ElsaPagedList
import cloud.elsa.app.designsystem.component.ElsaScaffold
import cloud.elsa.app.designsystem.component.ElsaScreenHeader
import cloud.elsa.app.designsystem.format.label
import cloud.elsa.app.designsystem.theme.ElsaTheme
import cloud.elsa.app.designsystem.token.ElsaSpacing
import cloud.elsa.app.feature.inbox.InboxEvent
import cloud.elsa.app.feature.inbox.InboxViewModel
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

    ElsaScaffold(
        onBack = onBack,
        containerColor = ElsaTheme.colors.backgroundGrouped,
        actions = {
            if (state.hasUnread) {
                ElsaButton(
                    text = stringResource(R.string.inbox_mark_all_read),
                    onClick = viewModel::markAllRead,
                    style = ElsaButtonStyle.Text,
                    size = ElsaButtonSize.Small,
                    fullWidth = false,
                )
            }
        },
    ) { padding ->
        ElsaPagedList(
            state = state.list,
            key = { it.id },
            emptyTitle = stringResource(R.string.inbox_empty),
            onRefresh = viewModel::refresh,
            onLoadMore = viewModel::loadMore,
            onRetry = viewModel::load,
            onRetryLoadMore = viewModel::retryLoadMore,
            modifier = Modifier.padding(padding),
            header = {
                item(key = "header") { ElsaScreenHeader(title = stringResource(R.string.inbox_title)) }
            },
        ) { item ->
            ElsaCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { viewModel.itemClicked(item) },
                verticalArrangement = Arrangement.spacedBy(ElsaSpacing.xxs),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ElsaSpacing.sm)) {
                    if (!item.read) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(ElsaTheme.colors.primary))
                    }
                    Text(
                        text = item.title,
                        style = if (item.read) ElsaTheme.typography.body else ElsaTheme.typography.bodyStrong,
                        color = if (item.read) ElsaTheme.colors.onSurfaceVariant else ElsaTheme.colors.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = RelativeTime.of(item.createdAt).label(),
                        style = ElsaTheme.typography.caption,
                        color = ElsaTheme.colors.onSurfaceTertiary,
                    )
                }
                item.body?.let {
                    Text(text = it, style = ElsaTheme.typography.bodySmall, color = ElsaTheme.colors.onSurfaceVariant)
                }
            }
        }
    }
}
