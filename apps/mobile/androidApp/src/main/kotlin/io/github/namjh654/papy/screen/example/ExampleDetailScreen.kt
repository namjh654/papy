package io.github.namjh654.papy.screen.example

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.namjh654.papy.R
import io.github.namjh654.papy.component.EventEffect
import io.github.namjh654.papy.component.message
import io.github.namjh654.papy.designsystem.component.PapyButton
import io.github.namjh654.papy.designsystem.component.PapyButtonStyle
import io.github.namjh654.papy.designsystem.component.PapyContentState
import io.github.namjh654.papy.designsystem.component.PapyFormColumn
import io.github.namjh654.papy.designsystem.component.PapyMessageType
import io.github.namjh654.papy.designsystem.component.PapyScaffold
import io.github.namjh654.papy.designsystem.component.PapyScreenHeader
import io.github.namjh654.papy.designsystem.component.rememberPapyToastState
import io.github.namjh654.papy.designsystem.theme.PapyTheme
import io.github.namjh654.papy.feature.example.detail.ExampleDetailEvent
import io.github.namjh654.papy.feature.example.detail.ExampleDetailViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * 상세 화면 기준 예시: 위쪽 뒤로가기 + 큰 제목 + 본문, 하단 고정 버튼.
 * 로딩·오류·내용 전환은 [PapyContentState] 가 한다. 인자(id)는 `koinViewModel { parametersOf(id) }` 로 넘긴다.
 */
@Composable
fun ExampleDetailScreen(
    id: String,
    onBack: () -> Unit,
) {
    val viewModel: ExampleDetailViewModel = koinViewModel { parametersOf(id) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val toast = rememberPapyToastState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    EventEffect(viewModel.events) { event ->
        when (event) {
            is ExampleDetailEvent.SaveFailed ->
                scope.launch { toast.show(event.error.message(context), PapyMessageType.Error) }
        }
    }

    PapyScaffold(onBack = onBack, toastState = toast) { padding ->
        PapyContentState(
            value = state.item,
            isLoading = state.isLoading,
            error = state.error,
            onRetry = viewModel::load,
            modifier = Modifier.padding(padding),
        ) { item ->
            PapyFormColumn(
                bottomBar = {
                    PapyButton(
                        text = stringResource(
                            if (item.favorite) R.string.example_detail_unfavorite else R.string.example_detail_favorite,
                        ),
                        onClick = viewModel::toggleFavorite,
                        style = if (item.favorite) PapyButtonStyle.Secondary else PapyButtonStyle.Primary,
                        isLoading = state.isSaving,
                    )
                },
            ) {
                PapyScreenHeader(title = item.title, description = item.subtitle)
                item.body?.let {
                    Text(text = it, style = PapyTheme.typography.body, color = PapyTheme.colors.onSurface)
                }
            }
        }
    }
}
