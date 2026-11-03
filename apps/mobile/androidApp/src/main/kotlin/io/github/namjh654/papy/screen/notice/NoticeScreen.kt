package io.github.namjh654.papy.screen.notice

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
import io.github.namjh654.papy.R
import io.github.namjh654.papy.component.EventEffect
import io.github.namjh654.papy.core.common.time.RelativeTime
import io.github.namjh654.papy.designsystem.component.PapyBadge
import io.github.namjh654.papy.designsystem.component.PapyCard
import io.github.namjh654.papy.designsystem.component.PapyPagedList
import io.github.namjh654.papy.designsystem.component.PapyScaffold
import io.github.namjh654.papy.designsystem.component.PapyScreenHeader
import io.github.namjh654.papy.designsystem.format.label
import io.github.namjh654.papy.designsystem.theme.PapyTheme
import io.github.namjh654.papy.designsystem.token.PapySpacing
import io.github.namjh654.papy.feature.notice.NoticeEvent
import io.github.namjh654.papy.feature.notice.NoticeViewModel
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

    PapyScaffold(onBack = onBack, containerColor = PapyTheme.colors.backgroundGrouped) { padding ->
        PapyPagedList(
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
                    PapyScreenHeader(
                        title = stringResource(R.string.notice_title),
                        description = stringResource(R.string.notice_description),
                    )
                }
            },
        ) { item ->
            PapyCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { viewModel.itemClicked(item) },
                verticalArrangement = Arrangement.spacedBy(PapySpacing.xxs),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(PapySpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.title,
                        style = PapyTheme.typography.subtitle,
                        color = PapyTheme.colors.onSurface,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (item.isNew) PapyBadge(text = stringResource(R.string.notice_new))
                }
                Text(
                    text = RelativeTime.of(item.publishedAt).label(),
                    style = PapyTheme.typography.bodySmall,
                    color = PapyTheme.colors.onSurfaceVariant,
                )
            }
        }
    }
}
