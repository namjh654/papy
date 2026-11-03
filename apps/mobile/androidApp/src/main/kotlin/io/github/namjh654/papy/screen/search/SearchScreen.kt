package io.github.namjh654.papy.screen.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.namjh654.papy.R
import io.github.namjh654.papy.component.EventEffect
import io.github.namjh654.papy.designsystem.component.PapyButton
import io.github.namjh654.papy.designsystem.component.PapyButtonSize
import io.github.namjh654.papy.designsystem.component.PapyButtonStyle
import io.github.namjh654.papy.designsystem.component.PapyCard
import io.github.namjh654.papy.designsystem.component.PapyEmptyState
import io.github.namjh654.papy.designsystem.component.PapyPagedList
import io.github.namjh654.papy.designsystem.component.PapyScaffold
import io.github.namjh654.papy.designsystem.component.PapyChip
import io.github.namjh654.papy.designsystem.component.PapyChipGroup
import io.github.namjh654.papy.designsystem.component.PapySearchBar
import io.github.namjh654.papy.designsystem.theme.PapyTheme
import io.github.namjh654.papy.designsystem.token.PapySize
import io.github.namjh654.papy.designsystem.token.PapySpacing
import io.github.namjh654.papy.feature.search.SearchEvent
import io.github.namjh654.papy.feature.search.SearchViewModel
import org.koin.androidx.compose.koinViewModel

/**
 * 검색: 위에 입력칸, 아래는 검색 전이면 최근 검색어, 검색 뒤면 결과 목록(빈 결과·오류 포함).
 * @param onItemSelected 결과를 눌렀을 때(id)
 */
@Composable
fun SearchScreen(
    onItemSelected: (String) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val viewModel: SearchViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }

    EventEffect(viewModel.events) { event ->
        when (event) {
            is SearchEvent.ItemSelected -> onItemSelected(event.id)
        }
    }

    // 들어오자마자 입력할 수 있게
    LaunchedEffect(Unit) { focus.requestFocus() }

    PapyScaffold(title = stringResource(R.string.search_title), onBack = onBack) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            PapySearchBar(
                value = state.query,
                onValueChange = viewModel::queryChanged,
                onSearch = viewModel::submit,
                modifier = Modifier
                    .padding(horizontal = PapySize.screenPadding, vertical = PapySpacing.sm)
                    .focusRequester(focus),
            )

            if (state.showRecent) {
                RecentSearches(state.recent, viewModel)
            } else {
                PapyPagedList(
                    state = state.results,
                    key = { it.id },
                    emptyTitle = stringResource(R.string.search_no_results, state.searched.orEmpty()),
                    onRefresh = viewModel::retry,
                    onLoadMore = viewModel::loadMore,
                    onRetry = viewModel::retry,
                    onRetryLoadMore = viewModel::retryLoadMore,
                ) { item ->
                    PapyCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { viewModel.itemClicked(item) },
                        verticalArrangement = Arrangement.spacedBy(PapySpacing.xxs),
                    ) {
                        Text(text = item.title, style = PapyTheme.typography.subtitle, color = PapyTheme.colors.onSurface)
                        item.subtitle?.let {
                            Text(text = it, style = PapyTheme.typography.bodySmall, color = PapyTheme.colors.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentSearches(recent: List<String>, viewModel: SearchViewModel) {
    if (recent.isEmpty()) {
        PapyEmptyState(title = stringResource(R.string.search_recent_empty))
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "header") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = PapySize.screenPadding, end = PapySpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.search_recent),
                    style = PapyTheme.typography.bodyStrong,
                    color = PapyTheme.colors.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                PapyButton(
                    text = stringResource(R.string.search_recent_clear),
                    onClick = viewModel::clearRecent,
                    style = PapyButtonStyle.Text,
                    size = PapyButtonSize.Small,
                    fullWidth = false,
                )
            }
        }
        // 최근 검색어는 칩(누르면 다시 검색, x 로 지우기) — 21-2
        item(key = "chips") {
            PapyChipGroup(modifier = Modifier.padding(horizontal = PapySize.screenPadding, vertical = PapySpacing.sm)) {
                recent.forEach { query ->
                    PapyChip(label = query, onClick = { viewModel.recentClicked(query) }, onRemove = { viewModel.removeRecent(query) })
                }
            }
        }
    }
}
