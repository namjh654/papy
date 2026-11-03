package io.github.namjh654.papy.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.namjh654.papy.component.message
import io.github.namjh654.papy.core.common.error.UiError
import io.github.namjh654.papy.designsystem.token.PapySize

/**
 * 한 건을 불러와 보여주는 화면(상세·수정)의 공통 전환: 값이 있으면 내용, 없으면 전체 로딩 또는 전체 오류(다시 시도).
 * 값이 있는 동안의 로딩·오류(수정 중, 수정 실패)는 화면이 버튼 로딩·토스트로 따로 보여준다. (iOS `PapyContentState`)
 *
 * ```
 * PapyContentState(value = state.item, isLoading = state.isLoading, error = state.error, onRetry = viewModel::load) { item -> … }
 * ```
 */
@Composable
fun <T : Any> PapyContentState(
    value: T?,
    isLoading: Boolean,
    error: UiError?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            value != null -> content(value)
            error != null -> PapyErrorState(
                message = error.message(),
                onRetry = onRetry,
                modifier = Modifier.align(Alignment.Center).padding(PapySize.screenPadding),
            )
            isLoading -> PapyLoadingIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}
