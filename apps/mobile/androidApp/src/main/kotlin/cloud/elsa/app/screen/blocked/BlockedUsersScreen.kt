package cloud.elsa.app.screen.blocked

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cloud.elsa.app.R
import cloud.elsa.app.component.EventEffect
import cloud.elsa.app.component.message
import cloud.elsa.app.designsystem.component.ElsaAvatar
import cloud.elsa.app.designsystem.component.ElsaButton
import cloud.elsa.app.designsystem.component.ElsaButtonSize
import cloud.elsa.app.designsystem.component.ElsaButtonStyle
import cloud.elsa.app.designsystem.component.ElsaEmptyState
import cloud.elsa.app.designsystem.component.ElsaErrorState
import cloud.elsa.app.designsystem.component.ElsaListItem
import cloud.elsa.app.designsystem.component.ElsaLoadingIndicator
import cloud.elsa.app.designsystem.component.ElsaMessageType
import cloud.elsa.app.designsystem.component.ElsaScaffold
import cloud.elsa.app.designsystem.component.ElsaScreenHeader
import cloud.elsa.app.designsystem.component.rememberElsaToastState
import cloud.elsa.app.designsystem.token.ElsaSize
import cloud.elsa.app.feature.community.blocked.BlockedUsersEvent
import cloud.elsa.app.feature.community.blocked.BlockedUsersViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/** 설정 → 차단한 사용자(App Store 1.2): 목록과 차단 해제 */
@Composable
fun BlockedUsersScreen(onBack: () -> Unit) {
    val viewModel: BlockedUsersViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val toast = rememberElsaToastState()
    val scope = rememberCoroutineScope()
    val unblockedText = stringResource(R.string.blocked_unblocked)

    EventEffect(viewModel.events) { event ->
        when (event) {
            is BlockedUsersEvent.Unblocked -> scope.launch { toast.show(unblockedText, ElsaMessageType.Success) }
        }
    }

    ElsaScaffold(onBack = onBack, toastState = toast) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                state.isLoading && state.users.isEmpty() -> ElsaLoadingIndicator(modifier = Modifier.align(Alignment.Center))
                state.error != null && state.users.isEmpty() -> ElsaErrorState(message = state.error!!.message(), onRetry = viewModel::load, modifier = Modifier.align(Alignment.Center))
                else -> LazyColumn {
                    item(key = "header") {
                        ElsaScreenHeader(title = stringResource(R.string.blocked_title), modifier = Modifier.padding(ElsaSize.screenPadding))
                    }
                    if (state.users.isEmpty()) {
                        item(key = "empty") { ElsaEmptyState(title = stringResource(R.string.blocked_empty)) }
                    }
                    items(state.users, key = { it.id }) { user ->
                        ElsaListItem(
                            title = user.name,
                            leading = { ElsaAvatar(name = user.name, url = user.avatarUrl, size = 40.dp) },
                            trailing = {
                                ElsaButton(
                                    text = stringResource(R.string.blocked_unblock),
                                    onClick = { viewModel.unblock(user) },
                                    style = ElsaButtonStyle.Secondary,
                                    size = ElsaButtonSize.Small,
                                    fullWidth = false,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
