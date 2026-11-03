package cloud.elsa.app.screen.notice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cloud.elsa.app.R
import cloud.elsa.app.component.EventEffect
import cloud.elsa.app.core.common.time.RelativeTime
import cloud.elsa.app.designsystem.component.ElsaBadge
import cloud.elsa.app.designsystem.component.ElsaCard
import cloud.elsa.app.designsystem.component.ElsaPagedList
import cloud.elsa.app.designsystem.component.ElsaScaffold
import cloud.elsa.app.designsystem.component.ElsaScreenHeader
import cloud.elsa.app.designsystem.format.label
import cloud.elsa.app.designsystem.theme.ElsaTheme
import cloud.elsa.app.designsystem.token.ElsaSpacing
import cloud.elsa.app.feature.notice.NoticeEvent
import cloud.elsa.app.feature.notice.NoticeViewModel
import org.koin.androidx.compose.koinViewModel

/**
 * 공지사항 목록: 큰 제목 + 회색 그룹 배경 위 무테 카드, 당겨서 새로고침, 끝에서 다음 페이지.
 * @param onItemSelected 상세 화면으로(id)
 * @param onBack 탭 루트면 null(뒤로가기 없음), 다른 화면 위에 열리면 뒤로가기
 */
@Composable
fun NoticeScreen(
    onItemSelected: (String) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val viewModel: NoticeViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    EventEffect(viewModel.events) { event ->
        when (event) {
            is NoticeEvent.ItemSelected -> onItemSelected(event.id)
        }
    }

    ElsaScaffold(onBack = onBack, containerColor = ElsaTheme.colors.backgroundGrouped) { padding ->
        ElsaPagedList(
            state = state.list,
            key = { it.id },
            emptyTitle = stringResource(R.string.notice_empty),
            onRefresh = viewModel::refresh,
            onLoadMore = viewModel::loadMore,
            onRetry = viewModel::load,
            onRetryLoadMore = viewModel::retryLoadMore,
            modifier = Modifier.padding(padding),
            header = {
                item(key = "header") {
                    ElsaScreenHeader(
                        title = stringResource(R.string.notice_title),
                        description = stringResource(R.string.notice_description),
                    )
                }
            },
        ) { item ->
            ElsaCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { viewModel.itemClicked(item) },
                verticalArrangement = Arrangement.spacedBy(ElsaSpacing.xxs),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ElsaSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.title,
                        style = ElsaTheme.typography.subtitle,
                        color = ElsaTheme.colors.onSurface,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (item.isNew) ElsaBadge(text = stringResource(R.string.notice_new))
                }
                Text(
                    text = RelativeTime.of(item.publishedAt).label(),
                    style = ElsaTheme.typography.bodySmall,
                    color = ElsaTheme.colors.onSurfaceVariant,
                )
            }
        }
    }
}
