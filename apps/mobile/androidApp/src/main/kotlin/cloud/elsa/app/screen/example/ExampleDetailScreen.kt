package cloud.elsa.app.screen.example

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cloud.elsa.app.R
import cloud.elsa.app.component.EventEffect
import cloud.elsa.app.component.message
import cloud.elsa.app.designsystem.component.ElsaButton
import cloud.elsa.app.designsystem.component.ElsaButtonStyle
import cloud.elsa.app.designsystem.component.ElsaContentState
import cloud.elsa.app.designsystem.component.ElsaFormColumn
import cloud.elsa.app.designsystem.component.ElsaMessageType
import cloud.elsa.app.designsystem.component.ElsaScaffold
import cloud.elsa.app.designsystem.component.ElsaScreenHeader
import cloud.elsa.app.designsystem.component.rememberElsaToastState
import cloud.elsa.app.designsystem.theme.ElsaTheme
import cloud.elsa.app.feature.example.detail.ExampleDetailEvent
import cloud.elsa.app.feature.example.detail.ExampleDetailViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * 상세 화면 기준 예시: 위쪽 뒤로가기 + 큰 제목 + 본문, 하단 고정 버튼.
 * 로딩·오류·내용 전환은 [ElsaContentState] 가 한다. 인자(id)는 `koinViewModel { parametersOf(id) }` 로 넘긴다.
 */
@Composable
fun ExampleDetailScreen(
    id: String,
    onBack: () -> Unit,
) {
    val viewModel: ExampleDetailViewModel = koinViewModel { parametersOf(id) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val toast = rememberElsaToastState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    EventEffect(viewModel.events) { event ->
        when (event) {
            is ExampleDetailEvent.SaveFailed ->
                scope.launch { toast.show(event.error.message(context), ElsaMessageType.Error) }
        }
    }

    ElsaScaffold(onBack = onBack, toastState = toast) { padding ->
        ElsaContentState(
            value = state.item,
            isLoading = state.isLoading,
            error = state.error,
            onRetry = viewModel::load,
            modifier = Modifier.padding(padding),
        ) { item ->
            ElsaFormColumn(
                bottomBar = {
                    ElsaButton(
                        text = stringResource(
                            if (item.favorite) R.string.example_detail_unfavorite else R.string.example_detail_favorite,
                        ),
                        onClick = viewModel::toggleFavorite,
                        style = if (item.favorite) ElsaButtonStyle.Secondary else ElsaButtonStyle.Primary,
                        isLoading = state.isSaving,
                    )
                },
            ) {
                ElsaScreenHeader(title = item.title, description = item.subtitle)
                item.body?.let {
                    Text(text = it, style = ElsaTheme.typography.body, color = ElsaTheme.colors.onSurface)
                }
            }
        }
    }
}
