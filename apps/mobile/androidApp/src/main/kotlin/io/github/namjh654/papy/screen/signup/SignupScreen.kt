package io.github.namjh654.papy.screen.signup

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.namjh654.papy.R
import io.github.namjh654.papy.component.EventEffect
import io.github.namjh654.papy.component.message
import io.github.namjh654.papy.designsystem.component.PapyButton
import io.github.namjh654.papy.designsystem.component.PapyFormColumn
import io.github.namjh654.papy.designsystem.component.PapyInlineMessage
import io.github.namjh654.papy.designsystem.component.PapyMessageType
import io.github.namjh654.papy.designsystem.component.PapyScaffold
import io.github.namjh654.papy.designsystem.component.PapyScreenHeader
import io.github.namjh654.papy.designsystem.component.PapyTextField
import io.github.namjh654.papy.feature.auth.signup.SignupEvent
import io.github.namjh654.papy.feature.auth.signup.SignupViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * 회원가입 화면
 *
 * UI 상태는 SignupViewModel의 SignupUiState를 구독하고,
 * 사용자 입력 및 액션은 ViewModel의 public method를 직접 호출합니다.
 * 화면 이동은 SignupEvent를 받아 NavHost가 전달한 콜백으로 처리합니다.
 */
@Composable
fun SignupScreen(
    marketingAgreed: Boolean,
    onSignupSucceeded: () -> Unit,
    onBackClick: () -> Unit,
) {
    val viewModel: SignupViewModel = koinViewModel { parametersOf(marketingAgreed) }

    val state by viewModel.uiState.collectAsStateWithLifecycle()

    EventEffect(viewModel.events) { event ->
        when (event) {
            is SignupEvent.SignupSucceeded -> onSignupSucceeded()
        }
    }

    PapyScaffold(onBack = onBackClick) { padding ->
        PapyFormColumn(
            modifier = Modifier.padding(padding),
            bottomBar = {
                PapyButton(
                    text = stringResource(R.string.signup_button),
                    onClick = viewModel::signup,
                    enabled = state.isSignupEnabled || state.isLoading,
                    isLoading = state.isLoading,
                )
            },
        ) {
            PapyScreenHeader(title = stringResource(R.string.signup_headline))

            PapyTextField(
                value = state.username,
                onValueChange = viewModel::usernameChanged,
                label = stringResource(R.string.field_username),
                placeholder = stringResource(R.string.field_username_placeholder),
            )

            PapyTextField(
                value = state.password,
                onValueChange = viewModel::passwordChanged,
                label = stringResource(R.string.field_password),
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
                onImeAction = { if (state.isSignupEnabled) viewModel.signup() },
            )

            state.error?.let { error ->
                PapyInlineMessage(message = error.message(), type = PapyMessageType.Error)
            }
        }
    }
}
