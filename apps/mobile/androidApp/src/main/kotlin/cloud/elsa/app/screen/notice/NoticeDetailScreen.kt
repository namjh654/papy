package cloud.elsa.app.screen.notice

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
import cloud.elsa.app.designsystem.component.ElsaContentState
import cloud.elsa.app.designsystem.component.ElsaScaffold
import cloud.elsa.app.designsystem.component.ElsaScreenHeader
import cloud.elsa.app.designsystem.format.ElsaFormat
import cloud.elsa.app.designsystem.format.currentLocale
import cloud.elsa.app.designsystem.theme.ElsaTheme
import cloud.elsa.app.designsystem.token.ElsaSize
import cloud.elsa.app.designsystem.token.ElsaSpacing
import cloud.elsa.app.feature.notice.detail.NoticeDetailViewModel
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

    ElsaScaffold(onBack = onBack) { padding ->
        ElsaContentState(
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
                    .padding(ElsaSize.screenPadding),
                verticalArrangement = Arrangement.spacedBy(ElsaSpacing.lg),
            ) {
                ElsaScreenHeader(title = item.title, description = ElsaFormat.date(item.publishedAt, locale))
                item.body?.let {
                    Text(text = it, style = ElsaTheme.typography.body, color = ElsaTheme.colors.onSurface)
                }
            }
        }
    }
}
