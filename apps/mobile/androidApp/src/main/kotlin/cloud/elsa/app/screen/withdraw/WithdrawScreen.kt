package cloud.elsa.app.screen.withdraw

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
import cloud.elsa.app.R
import cloud.elsa.app.component.EventEffect
import cloud.elsa.app.component.message
import cloud.elsa.app.designsystem.component.ElsaAlert
import cloud.elsa.app.designsystem.component.ElsaAlertDialog
import cloud.elsa.app.designsystem.component.ElsaButton
import cloud.elsa.app.designsystem.component.ElsaButtonStyle
import cloud.elsa.app.designsystem.component.ElsaCheckbox
import cloud.elsa.app.designsystem.component.ElsaFormColumn
import cloud.elsa.app.designsystem.component.ElsaInlineMessage
import cloud.elsa.app.designsystem.component.ElsaMessageType
import cloud.elsa.app.designsystem.component.ElsaScaffold
import cloud.elsa.app.designsystem.component.ElsaScreenHeader
import cloud.elsa.app.designsystem.component.ElsaTextField
import cloud.elsa.app.feature.settings.withdraw.WithdrawEvent
import cloud.elsa.app.feature.settings.withdraw.WithdrawViewModel
import org.koin.androidx.compose.koinViewModel

/** 회원 탈퇴: 안내 → 본인 확인 → 체크 → 확인 다이얼로그 → 요청 */
@Composable
fun WithdrawScreen(
    onWithdrawn: () -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: WithdrawViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var alert by remember { mutableStateOf<ElsaAlert?>(null) }

    EventEffect(viewModel.events) { event ->
        when (event) {
            WithdrawEvent.Withdrawn -> onWithdrawn()
        }
    }

    val buttonText = stringResource(R.string.withdraw_button)
    val confirmTitle = stringResource(R.string.withdraw_confirm_title)
    val confirmMessage = stringResource(R.string.withdraw_confirm_message)
    val cancelText = stringResource(R.string.common_cancel)

    ElsaScaffold(onBack = onBack) { padding ->
        ElsaFormColumn(
            modifier = Modifier.padding(padding),
            bottomBar = {
                ElsaButton(
                    text = buttonText,
                    onClick = {
                        alert = ElsaAlert(
                            title = confirmTitle,
                            message = confirmMessage,
                            confirmText = buttonText,
                            dismissText = cancelText,
                            isDestructive = true,
                            onConfirm = viewModel::withdraw,
                        )
                    },
                    style = ElsaButtonStyle.Destructive,
                    enabled = state.canSubmit || state.isLoading,
                    isLoading = state.isLoading,
                )
            },
        ) {
            ElsaScreenHeader(
                title = stringResource(R.string.withdraw_title),
                description = stringResource(R.string.withdraw_description),
            )

            ElsaInlineMessage(message = stringResource(R.string.withdraw_notice), type = ElsaMessageType.Warning)

            // 소셜로 가입한 사용자는 비밀번호가 없다 — 체크만으로 본인 의사를 확인한다(18-4)
            if (state.requiresPassword) ElsaTextField(
                value = state.password,
                onValueChange = viewModel::passwordChanged,
                label = stringResource(R.string.field_password),
                placeholder = stringResource(R.string.field_password_placeholder),
                helperText = stringResource(R.string.withdraw_password_helper),
                isSecure = true,
                imeAction = ImeAction.Done,
            )

            ElsaCheckbox(
                checked = state.confirmed,
                onCheckedChange = viewModel::confirmedChanged,
                label = stringResource(R.string.withdraw_agree),
            )

            state.error?.let { error ->
                ElsaInlineMessage(message = error.message(), type = ElsaMessageType.Error)
            }
        }
    }

    ElsaAlertDialog(alert = alert, onDismiss = { alert = null })
}
