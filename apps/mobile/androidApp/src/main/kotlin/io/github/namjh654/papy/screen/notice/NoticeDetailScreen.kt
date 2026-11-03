package io.github.namjh654.papy.screen.notice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.namjh654.papy.designsystem.component.PapyContentState
import io.github.namjh654.papy.designsystem.component.PapyScaffold
import io.github.namjh654.papy.designsystem.component.PapyScreenHeader
import io.github.namjh654.papy.designsystem.format.PapyFormat
import io.github.namjh654.papy.designsystem.format.currentLocale
import io.github.namjh654.papy.designsystem.theme.PapyTheme
import io.github.namjh654.papy.designsystem.token.PapySize
import io.github.namjh654.papy.designsystem.token.PapySpacing
import io.github.namjh654.papy.feature.notice.detail.NoticeDetailViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/** 공지 상세: 큰 제목 + 날짜 + 본문(스크롤) */
@Composable
fun NoticeDetailScreen(
    id: String,
    onBack: () -> Unit,
) {
    val viewModel: NoticeDetailViewModel = koinViewModel { parametersOf(id) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val locale = currentLocale

    PapyScaffold(onBack = onBack) { padding ->
        PapyContentState(
            value = state.item,
            isLoading = state.isLoading,
            error = state.error,
            onRetry = viewModel::load,
            modifier = Modifier.padding(padding),
        ) { item ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(PapySize.screenPadding),
                verticalArrangement = Arrangement.spacedBy(PapySpacing.lg),
            ) {
                PapyScreenHeader(title = item.title, description = PapyFormat.date(item.publishedAt, locale))
                item.body?.let {
                    Text(text = it, style = PapyTheme.typography.body, color = PapyTheme.colors.onSurface)
                }
            }
        }
    }
}
