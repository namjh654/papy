package io.github.namjh654.papy.screen.withdraw

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.namjh654.papy.R
import io.github.namjh654.papy.component.EventEffect
import io.github.namjh654.papy.component.message
import io.github.namjh654.papy.designsystem.component.PapyAlert
import io.github.namjh654.papy.designsystem.component.PapyAlertDialog
import io.github.namjh654.papy.designsystem.component.PapyButton
import io.github.namjh654.papy.designsystem.component.PapyButtonStyle
import io.github.namjh654.papy.designsystem.component.PapyCheckbox
import io.github.namjh654.papy.designsystem.component.PapyFormColumn
import io.github.namjh654.papy.designsystem.component.PapyInlineMessage
import io.github.namjh654.papy.designsystem.component.PapyMessageType
import io.github.namjh654.papy.designsystem.component.PapyScaffold
import io.github.namjh654.papy.designsystem.component.PapyScreenHeader
import io.github.namjh654.papy.designsystem.component.PapyTextField
import io.github.namjh654.papy.feature.settings.withdraw.WithdrawEvent
import io.github.namjh654.papy.feature.settings.withdraw.WithdrawViewModel
import org.koin.androidx.compose.koinViewModel

/** 회원 탈퇴: 안내 → 본인 확인 → 체크 → 확인 다이얼로그 → 요청 */
@Composable
fun WithdrawScreen(
    onWithdrawn: () -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: WithdrawViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var alert by remember { mutableStateOf<PapyAlert?>(null) }

    EventEffect(viewModel.events) { event ->
        when (event) {
            WithdrawEvent.Withdrawn -> onWithdrawn()
        }
    }

    val buttonText = stringResource(R.string.withdraw_button)
    val confirmTitle = stringResource(R.string.withdraw_confirm_title)
    val confirmMessage = stringResource(R.string.withdraw_confirm_message)
    val cancelText = stringResource(R.string.common_cancel)

    PapyScaffold(onBack = onBack) { padding ->
        PapyFormColumn(
            modifier = Modifier.padding(padding),
            bottomBar = {
                PapyButton(
                    text = buttonText,
                    onClick = {
                        alert = PapyAlert(
                            title = confirmTitle,
                            message = confirmMessage,
                            confirmText = buttonText,
                            dismissText = cancelText,
                            isDestructive = true,
                            onConfirm = viewModel::withdraw,
                        )
                    },
                    style = PapyButtonStyle.Destructive,
                    enabled = state.canSubmit || state.isLoading,
                    isLoading = state.isLoading,
                )
            },
        ) {
            PapyScreenHeader(
                title = stringResource(R.string.withdraw_title),
                description = stringResource(R.string.withdraw_description),
            )

            PapyInlineMessage(message = stringResource(R.string.withdraw_notice), type = PapyMessageType.Warning)

            // 소셜로 가입한 사용자는 비밀번호가 없다 — 체크만으로 본인 의사를 확인한다(18-4)
            if (state.requiresPassword) PapyTextField(
                value = state.password,
                onValueChange = viewModel::passwordChanged,
                label = stringResource(R.string.field_password),
                placeholder = stringResource(R.string.field_password_placeholder),
                helperText = stringResource(R.string.withdraw_password_helper),
                isSecure = true,
                imeAction = ImeAction.Done,
            )

            PapyCheckbox(
                checked = state.confirmed,
                onCheckedChange = viewModel::confirmedChanged,
                label = stringResource(R.string.withdraw_agree),
            )

            state.error?.let { error ->
                PapyInlineMessage(message = error.message(), type = PapyMessageType.Error)
            }
        }
    }

    PapyAlertDialog(alert = alert, onDismiss = { alert = null })
}
