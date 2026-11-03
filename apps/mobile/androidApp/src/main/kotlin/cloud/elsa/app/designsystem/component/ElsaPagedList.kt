package cloud.elsa.app.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import cloud.elsa.app.R
import cloud.elsa.app.component.message
import cloud.elsa.app.core.common.paging.ListState
import cloud.elsa.app.designsystem.token.ElsaSize
import cloud.elsa.app.designsystem.token.ElsaSpacing

/**
 * 목록 화면 공통 틀 (KMP `ListState` + `Paginator`).
 * 당겨서 새로고침, 끝에서 [PREFETCH] 개 전에 다음 페이지, 첫 로딩·전체 오류·빈 화면·다음 페이지 실패를 한 곳에서 처리한다.
 *
 * ```
 * ElsaPagedList(
 *     state = state.list, key = { it.id }, emptyTitle = stringResource(R.string.notice_empty),
 *     onRefresh = viewModel::refresh, onLoadMore = viewModel::loadMore,
 *     onRetry = viewModel::load, onRetryLoadMore = viewModel::retryLoadMore,
 *     header = { item { ElsaScreenHeader(…) } },
 * ) { item -> ElsaCard(onClick = { viewModel.itemClicked(item) }) { … } }
 * ```
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ElsaPagedList(
    state: ListState<T>,
    key: (T) -> Any,
    emptyTitle: String,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onRetryLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(ElsaSize.screenPadding),
    header: LazyListScope.() -> Unit = {},
    /** 맨 위로 올리기 등 화면이 스크롤을 다룰 때 넘긴다 */
    listState: LazyListState = rememberLazyListState(),
    /** 첫 로딩 자리(스켈레톤 등). 없으면 가운데 로딩 표시 */
    loadingContent: (@Composable () -> Unit)? = null,
    itemContent: @Composable (T) -> Unit,
) {
    val loadMore by rememberUpdatedState(onLoadMore)
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            last >= info.totalItemsCount - 1 - PREFETCH
        }
    }
    LaunchedEffect(nearEnd, state.canLoadMore, state.items.size) {
        if (nearEnd && state.canLoadMore && state.items.isNotEmpty()) loadMore()
    }

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(ElsaSpacing.md),
        ) {
            header()

            // 목록이 있는데 새로고침이 실패했으면 목록 위에 짧게 알린다
            if (state.error != null && state.items.isNotEmpty()) {
                item(key = "elsa-refresh-error") {
                    ElsaInlineMessage(message = state.error!!.message(), type = ElsaMessageType.Error)
                }
            }

            when {
                state.isLoading && state.items.isEmpty() -> item(key = "elsa-loading") { loadingContent?.invoke() ?: CenteredLoading() }
                state.showFullError -> item(key = "elsa-error") { ElsaErrorState(message = state.error!!.message(), onRetry = onRetry) }
                state.isEmpty -> item(key = "elsa-empty") { ElsaEmptyState(title = emptyTitle) }
                else -> items(state.items, key = key) { itemContent(it) }
            }

            if (state.isLoadingMore) item(key = "elsa-loading-more") { CenteredLoading() }
            state.loadMoreError?.let { error ->
                item(key = "elsa-load-more-error") {
                    ElsaInlineMessage(message = error.message(), type = ElsaMessageType.Error)
                    ElsaButton(
                        text = stringResource(R.string.common_retry),
                        onClick = onRetryLoadMore,
                        modifier = Modifier.fillMaxWidth().padding(top = ElsaSpacing.sm),
                        style = ElsaButtonStyle.Secondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun CenteredLoading() {
    Box(Modifier.fillMaxWidth().padding(ElsaSpacing.xl), contentAlignment = Alignment.Center) {
        ElsaLoadingIndicator()
    }
}

/** 끝에서 몇 개 전에 다음 페이지를 요청할지 */
private const val PREFETCH = 3
