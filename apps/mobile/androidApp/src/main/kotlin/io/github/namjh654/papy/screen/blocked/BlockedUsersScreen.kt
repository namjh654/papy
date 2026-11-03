package io.github.namjh654.papy.screen.blocked

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
import io.github.namjh654.papy.R
import io.github.namjh654.papy.component.EventEffect
import io.github.namjh654.papy.component.message
import io.github.namjh654.papy.designsystem.component.PapyAvatar
import io.github.namjh654.papy.designsystem.component.PapyButton
import io.github.namjh654.papy.designsystem.component.PapyButtonSize
import io.github.namjh654.papy.designsystem.component.PapyButtonStyle
import io.github.namjh654.papy.designsystem.component.PapyEmptyState
import io.github.namjh654.papy.designsystem.component.PapyErrorState
import io.github.namjh654.papy.designsystem.component.PapyListItem
import io.github.namjh654.papy.designsystem.component.PapyLoadingIndicator
import io.github.namjh654.papy.designsystem.component.PapyMessageType
import io.github.namjh654.papy.designsystem.component.PapyScaffold
import io.github.namjh654.papy.designsystem.component.PapyScreenHeader
import io.github.namjh654.papy.designsystem.component.rememberPapyToastState
import io.github.namjh654.papy.designsystem.token.PapySize
import io.github.namjh654.papy.feature.community.blocked.BlockedUsersEvent
import io.github.namjh654.papy.feature.community.blocked.BlockedUsersViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/** 설정 → 차단한 사용자(App Store 1.2): 목록과 차단 해제 */
@Composable
fun BlockedUsersScreen(onBack: () -> Unit) {
    val viewModel: BlockedUsersViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val toast = rememberPapyToastState()
    val scope = rememberCoroutineScope()
    val unblockedText = stringResource(R.string.blocked_unblocked)

    EventEffect(viewModel.events) { event ->
        when (event) {
            is BlockedUsersEvent.Unblocked -> scope.launch { toast.show(unblockedText, PapyMessageType.Success) }
        }
    }

    PapyScaffold(onBack = onBack, toastState = toast) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                state.isLoading && state.users.isEmpty() -> PapyLoadingIndicator(modifier = Modifier.align(Alignment.Center))
                state.error != null && state.users.isEmpty() -> PapyErrorState(message = state.error!!.message(), onRetry = viewModel::load, modifier = Modifier.align(Alignment.Center))
                else -> LazyColumn {
                    item(key = "header") {
                        PapyScreenHeader(title = stringResource(R.string.blocked_title), modifier = Modifier.padding(PapySize.screenPadding))
                    }
                    if (state.users.isEmpty()) {
                        item(key = "empty") { PapyEmptyState(title = stringResource(R.string.blocked_empty)) }
                    }
                    items(state.users, key = { it.id }) { user ->
                        PapyListItem(
                            title = user.name,
                            leading = { PapyAvatar(name = user.name, url = user.avatarUrl, size = 40.dp) },
                            trailing = {
                                PapyButton(
                                    text = stringResource(R.string.blocked_unblock),
                                    onClick = { viewModel.unblock(user) },
                                    style = PapyButtonStyle.Secondary,
                                    size = PapyButtonSize.Small,
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
