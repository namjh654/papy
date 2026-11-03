package io.github.namjh654.papy.screen.reset

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
import io.github.namjh654.papy.R
import io.github.namjh654.papy.component.EventEffect
import io.github.namjh654.papy.component.message
import io.github.namjh654.papy.designsystem.component.PapyButton
import io.github.namjh654.papy.designsystem.component.PapyButtonSize
import io.github.namjh654.papy.designsystem.component.PapyButtonStyle
import io.github.namjh654.papy.designsystem.component.PapyFormColumn
import io.github.namjh654.papy.designsystem.component.PapyInlineMessage
import io.github.namjh654.papy.designsystem.component.PapyMessageType
import io.github.namjh654.papy.designsystem.component.PapyScaffold
import io.github.namjh654.papy.designsystem.component.PapyScreenHeader
import io.github.namjh654.papy.designsystem.component.PapyTextField
import io.github.namjh654.papy.designsystem.component.rememberPapyToastState
import io.github.namjh654.papy.feature.auth.reset.PasswordResetEvent
import io.github.namjh654.papy.feature.auth.reset.PasswordResetStep
import io.github.namjh654.papy.feature.auth.reset.PasswordResetViewModel
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
    val toast = rememberPapyToastState()
    val scope = rememberCoroutineScope()
    val resentText = stringResource(R.string.reset_resent)

    EventEffect(viewModel.events) { event ->
        when (event) {
            PasswordResetEvent.CodeResent -> scope.launch { toast.show(resentText, PapyMessageType.Success) }
            PasswordResetEvent.Done -> onDone()
        }
    }

    // 둘째 단계에서만 시스템 뒤로가기를 가로챈다
    BackHandler(enabled = state.canGoToPreviousStep, onBack = viewModel::previousStep)

    PapyScaffold(
        onBack = { if (state.canGoToPreviousStep) viewModel.previousStep() else onBack() },
        toastState = toast,
    ) { padding ->
        PapyFormColumn(
            modifier = Modifier.padding(padding),
            bottomBar = {
                when (state.step) {
                    PasswordResetStep.Request -> PapyButton(
                        text = stringResource(R.string.reset_request_button),
                        onClick = viewModel::requestCode,
                        enabled = state.canRequest || state.isLoading,
                        isLoading = state.isLoading,
                    )
                    PasswordResetStep.Confirm -> PapyButton(
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
                    PapyScreenHeader(
                        title = stringResource(R.string.reset_title),
                        description = stringResource(R.string.reset_request_description),
                    )
                    PapyTextField(
                        value = state.username,
                        onValueChange = viewModel::usernameChanged,
                        label = stringResource(R.string.field_username),
                        placeholder = stringResource(R.string.field_username_placeholder),
                        imeAction = ImeAction.Done,
                        onImeAction = { if (state.canRequest) viewModel.requestCode() },
                    )
                }
                PasswordResetStep.Confirm -> {
                    PapyScreenHeader(
                        title = stringResource(R.string.reset_title),
                        description = stringResource(R.string.reset_confirm_description, state.username),
                    )
                    PapyTextField(
                        value = state.code,
                        onValueChange = viewModel::codeChanged,
                        label = stringResource(R.string.field_code),
                        placeholder = stringResource(R.string.field_code_placeholder),
                        keyboardType = KeyboardType.Number,
                    )
                    PapyTextField(
                        value = state.password,
                        onValueChange = viewModel::passwordChanged,
                        label = stringResource(R.string.field_new_password),
                        placeholder = stringResource(R.string.field_new_password_placeholder),
                        isSecure = true,
                    )
                    PapyTextField(
                        value = state.passwordConfirmation,
                        onValueChange = viewModel::passwordConfirmationChanged,
                        label = stringResource(R.string.field_password_confirm),
                        placeholder = stringResource(R.string.field_password_confirm_placeholder),
                        isSecure = true,
                        imeAction = ImeAction.Done,
                        onImeAction = { if (state.canConfirm) viewModel.confirm() },
                    )
                    PapyButton(
                        text = stringResource(R.string.reset_resend),
                        onClick = viewModel::resendCode,
                        style = PapyButtonStyle.Text,
                        size = PapyButtonSize.Small,
                        fullWidth = false,
                        enabled = !state.isLoading,
                    )
                }
            }

            state.error?.let { error ->
                PapyInlineMessage(message = error.message(), type = PapyMessageType.Error)
            }
        }
    }
}
