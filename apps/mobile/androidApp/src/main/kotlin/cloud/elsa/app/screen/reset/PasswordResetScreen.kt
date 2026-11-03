package cloud.elsa.app.screen.reset

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cloud.elsa.app.R
import cloud.elsa.app.component.EventEffect
import cloud.elsa.app.component.message
import cloud.elsa.app.designsystem.component.ElsaButton
import cloud.elsa.app.designsystem.component.ElsaButtonSize
import cloud.elsa.app.designsystem.component.ElsaButtonStyle
import cloud.elsa.app.designsystem.component.ElsaFormColumn
import cloud.elsa.app.designsystem.component.ElsaInlineMessage
import cloud.elsa.app.designsystem.component.ElsaMessageType
import cloud.elsa.app.designsystem.component.ElsaScaffold
import cloud.elsa.app.designsystem.component.ElsaScreenHeader
import cloud.elsa.app.designsystem.component.ElsaTextField
import cloud.elsa.app.designsystem.component.rememberElsaToastState
import cloud.elsa.app.feature.auth.reset.PasswordResetEvent
import cloud.elsa.app.feature.auth.reset.PasswordResetStep
import cloud.elsa.app.feature.auth.reset.PasswordResetViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * 비밀번호 재설정: 단계에 따라 입력칸과 하단 버튼이 바뀐다.
 * 둘째 단계에서는 뒤로가기(위쪽 화살표·시스템)가 첫 단계로 간다.
 */
@Composable
fun PasswordResetScreen(
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: PasswordResetViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val toast = rememberElsaToastState()
    val scope = rememberCoroutineScope()
    val resentText = stringResource(R.string.reset_resent)

    EventEffect(viewModel.events) { event ->
        when (event) {
            PasswordResetEvent.CodeResent -> scope.launch { toast.show(resentText, ElsaMessageType.Success) }
            PasswordResetEvent.Done -> onDone()
        }
    }

    // 둘째 단계에서만 시스템 뒤로가기를 가로챈다
    BackHandler(enabled = state.canGoToPreviousStep, onBack = viewModel::previousStep)

    ElsaScaffold(
        onBack = { if (state.canGoToPreviousStep) viewModel.previousStep() else onBack() },
        toastState = toast,
    ) { padding ->
        ElsaFormColumn(
            modifier = Modifier.padding(padding),
            bottomBar = {
                when (state.step) {
                    PasswordResetStep.Request -> ElsaButton(
                        text = stringResource(R.string.reset_request_button),
                        onClick = viewModel::requestCode,
                        enabled = state.canRequest || state.isLoading,
                        isLoading = state.isLoading,
                    )
                    PasswordResetStep.Confirm -> ElsaButton(
                        text = stringResource(R.string.reset_confirm_button),
                        onClick = viewModel::confirm,
                        enabled = state.canConfirm || state.isLoading,
                        isLoading = state.isLoading,
                    )
                }
            },
        ) {
            when (state.step) {
                PasswordResetStep.Request -> {
                    ElsaScreenHeader(
                        title = stringResource(R.string.reset_title),
                        description = stringResource(R.string.reset_request_description),
                    )
                    ElsaTextField(
                        value = state.username,
                        onValueChange = viewModel::usernameChanged,
                        label = stringResource(R.string.field_username),
                        placeholder = stringResource(R.string.field_username_placeholder),
                        imeAction = ImeAction.Done,
                        onImeAction = { if (state.canRequest) viewModel.requestCode() },
                    )
                }
                PasswordResetStep.Confirm -> {
                    ElsaScreenHeader(
                        title = stringResource(R.string.reset_title),
                        description = stringResource(R.string.reset_confirm_description, state.username),
                    )
                    ElsaTextField(
                        value = state.code,
                        onValueChange = viewModel::codeChanged,
                        label = stringResource(R.string.field_code),
                        placeholder = stringResource(R.string.field_code_placeholder),
                        keyboardType = KeyboardType.Number,
                    )
                    ElsaTextField(
                        value = state.password,
                        onValueChange = viewModel::passwordChanged,
                        label = stringResource(R.string.field_new_password),
                        placeholder = stringResource(R.string.field_new_password_placeholder),
                        isSecure = true,
                    )
                    ElsaTextField(
                        value = state.passwordConfirmation,
                        onValueChange = viewModel::passwordConfirmationChanged,
                        label = stringResource(R.string.field_password_confirm),
                        placeholder = stringResource(R.string.field_password_confirm_placeholder),
                        isSecure = true,
                        imeAction = ImeAction.Done,
                        onImeAction = { if (state.canConfirm) viewModel.confirm() },
                    )
                    ElsaButton(
                        text = stringResource(R.string.reset_resend),
                        onClick = viewModel::resendCode,
                        style = ElsaButtonStyle.Text,
                        size = ElsaButtonSize.Small,
                        fullWidth = false,
                        enabled = !state.isLoading,
                    )
                }
            }

            state.error?.let { error ->
                ElsaInlineMessage(message = error.message(), type = ElsaMessageType.Error)
            }
        }
    }
}
