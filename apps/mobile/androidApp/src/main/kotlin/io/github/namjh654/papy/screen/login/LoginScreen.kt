package io.github.namjh654.papy.screen.login

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.namjh654.papy.R
import io.github.namjh654.papy.component.EventEffect
import io.github.namjh654.papy.component.message
import io.github.namjh654.papy.designsystem.component.PapyButton
import io.github.namjh654.papy.designsystem.component.PapyButtonSize
import io.github.namjh654.papy.designsystem.component.PapyButtonStyle
import io.github.namjh654.papy.designsystem.component.PapyCheckbox
import io.github.namjh654.papy.designsystem.component.PapyFormColumn
import io.github.namjh654.papy.designsystem.component.PapyInlineMessage
import io.github.namjh654.papy.designsystem.component.PapyMessageType
import io.github.namjh654.papy.designsystem.component.PapyScaffold
import io.github.namjh654.papy.designsystem.component.PapyScreenHeader
import io.github.namjh654.papy.designsystem.component.PapySocialLoginSection
import io.github.namjh654.papy.designsystem.component.PapyTextField
import io.github.namjh654.papy.designsystem.component.rememberPapyToastState
import io.github.namjh654.papy.designsystem.theme.PapyTheme
import io.github.namjh654.papy.designsystem.token.PapySpacing
import io.github.namjh654.papy.feature.auth.login.LoginEvent
import io.github.namjh654.papy.feature.auth.login.LoginNotice
import io.github.namjh654.papy.feature.auth.login.LoginViewModel
import io.github.namjh654.papy.social.AndroidSocialSdk
import io.github.namjh654.papy.social.SocialSignInResult
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun LoginScreen(
    onLoginSucceeded: () -> Unit,
    onSocialSignedUp: () -> Unit,
    onSignupClick: () -> Unit,
    onPasswordResetClick: () -> Unit,
) {
    val viewModel: LoginViewModel = koinViewModel()
    val activity = LocalActivity.current

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val toast = rememberPapyToastState()
    val scope = rememberCoroutineScope()
    val signedUpText = stringResource(R.string.login_notice_signed_up)
    val passwordChangedText = stringResource(R.string.login_notice_password_changed)

    EventEffect(viewModel.events) { event ->
        when (event) {
            is LoginEvent.LoginSucceeded -> onLoginSucceeded()
            // 소셜(DEC-105~109): 플랫폼 SDK 로 인증 → 결과를 KMP 로(dev mock 은 이 이벤트 없이 바로 로그인)
            is LoginEvent.StartSocialSignIn -> scope.launch {
                val result = activity?.let { AndroidSocialSdk.signIn(it, event.provider) }
                    ?: SocialSignInResult.Failed(IllegalStateException("Activity 없음"))
                when (result) {
                    is SocialSignInResult.Success -> viewModel.socialCredentialReceived(result.credential)
                    SocialSignInResult.Cancelled -> viewModel.socialSignInCancelled()
                    is SocialSignInResult.Failed -> viewModel.socialSignInFailed()
                }
            }
            // 처음 가입한 소셜 사용자: 약관 동의를 받고 메인으로(18-4)
            is LoginEvent.SocialSignedUp -> onSocialSignedUp()
            // 회원가입·비밀번호 재설정을 마치고 돌아왔을 때
            is LoginEvent.Notice -> scope.launch {
                val text = when (event.notice) {
                    LoginNotice.SignedUp -> signedUpText
                    LoginNotice.PasswordChanged -> passwordChangedText
                }
                toast.show(text, PapyMessageType.Success)
            }
        }
    }

    val canLogin = state.isLoginEnabled && !state.isLoading

    PapyScaffold(toastState = toast) { padding ->
    PapyFormColumn(
        modifier = Modifier.padding(padding),
        bottomBar = {
            PapyButton(
                text = stringResource(R.string.login_button),
                onClick = viewModel::login,
                enabled = state.isLoginEnabled,
                isLoading = state.isLoading,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.login_no_account),
                    style = PapyTheme.typography.bodySmall,
                    color = PapyTheme.colors.onSurfaceVariant,
                )
                PapyButton(
                    text = stringResource(R.string.login_signup),
                    onClick = onSignupClick,
                    style = PapyButtonStyle.Text,
                    size = PapyButtonSize.Small,
                    fullWidth = false,
                )
            }
        },
    ) {
        Spacer(modifier = Modifier.height(PapySpacing.xxl))

        PapyScreenHeader(
            title = stringResource(R.string.login_title),
            description = stringResource(R.string.login_description),
        )

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
            placeholder = stringResource(R.string.field_password_placeholder),
            isSecure = true,
            imeAction = ImeAction.Done,
            onImeAction = { if (canLogin) viewModel.login() },
        )

        // 한 줄에 안 들어가면(영어·큰 글자) 다음 줄로 — 글자가 한 글자씩 갈라지지 않게
        FlowRow(horizontalArrangement = Arrangement.spacedBy(PapySpacing.lg)) {
            PapyCheckbox(
                checked = state.rememberId,
                onCheckedChange = viewModel::rememberIdChanged,
                label = stringResource(R.string.login_remember_id),
            )
            PapyCheckbox(
                checked = state.autoLogin,
                onCheckedChange = viewModel::autoLoginChanged,
                label = stringResource(R.string.login_auto_login),
            )
        }

        state.error?.let { error ->
            PapyInlineMessage(message = error.message(), type = PapyMessageType.Error)
        }

        PapyButton(
            text = stringResource(R.string.login_forgot_password),
            onClick = onPasswordResetClick,
            style = PapyButtonStyle.Text,
            size = PapyButtonSize.Small,
            fullWidth = false,
            modifier = Modifier.align(Alignment.End),
        )

        PapySocialLoginSection(
            providers = state.socialProviders,
            onClick = viewModel::socialLoginTapped,
            enabled = !state.isLoading,
        )
    }
    }
}
