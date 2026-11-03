package cloud.elsa.app.screen.login

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
import cloud.elsa.app.R
import cloud.elsa.app.component.EventEffect
import cloud.elsa.app.component.message
import cloud.elsa.app.designsystem.component.ElsaButton
import cloud.elsa.app.designsystem.component.ElsaButtonSize
import cloud.elsa.app.designsystem.component.ElsaButtonStyle
import cloud.elsa.app.designsystem.component.ElsaCheckbox
import cloud.elsa.app.designsystem.component.ElsaFormColumn
import cloud.elsa.app.designsystem.component.ElsaInlineMessage
import cloud.elsa.app.designsystem.component.ElsaMessageType
import cloud.elsa.app.designsystem.component.ElsaScaffold
import cloud.elsa.app.designsystem.component.ElsaScreenHeader
import cloud.elsa.app.designsystem.component.ElsaSocialLoginSection
import cloud.elsa.app.designsystem.component.ElsaTextField
import cloud.elsa.app.designsystem.component.rememberElsaToastState
import cloud.elsa.app.designsystem.theme.ElsaTheme
import cloud.elsa.app.designsystem.token.ElsaSpacing
import cloud.elsa.app.feature.auth.login.LoginEvent
import cloud.elsa.app.feature.auth.login.LoginNotice
import cloud.elsa.app.feature.auth.login.LoginViewModel
import cloud.elsa.app.social.AndroidSocialSdk
import cloud.elsa.app.social.SocialSignInResult
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
    val toast = rememberElsaToastState()
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
                toast.show(text, ElsaMessageType.Success)
            }
        }
    }

    val canLogin = state.isLoginEnabled && !state.isLoading

    ElsaScaffold(toastState = toast) { padding ->
    ElsaFormColumn(
        modifier = Modifier.padding(padding),
        bottomBar = {
            ElsaButton(
                text = stringResource(R.string.login_button),
                onClick = viewModel::login,
                enabled = state.isLoginEnabled,
                isLoading = state.isLoading,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.login_no_account),
                    style = ElsaTheme.typography.bodySmall,
                    color = ElsaTheme.colors.onSurfaceVariant,
                )
                ElsaButton(
                    text = stringResource(R.string.login_signup),
                    onClick = onSignupClick,
                    style = ElsaButtonStyle.Text,
                    size = ElsaButtonSize.Small,
                    fullWidth = false,
                )
            }
        },
    ) {
        Spacer(modifier = Modifier.height(ElsaSpacing.xxl))

        ElsaScreenHeader(
            title = stringResource(R.string.login_title),
            description = stringResource(R.string.login_description),
        )

        ElsaTextField(
            value = state.username,
            onValueChange = viewModel::usernameChanged,
            label = stringResource(R.string.field_username),
            placeholder = stringResource(R.string.field_username_placeholder),
        )

        ElsaTextField(
            value = state.password,
            onValueChange = viewModel::passwordChanged,
            label = stringResource(R.string.field_password),
            placeholder = stringResource(R.string.field_password_placeholder),
            isSecure = true,
            imeAction = ImeAction.Done,
            onImeAction = { if (canLogin) viewModel.login() },
        )

        // 한 줄에 안 들어가면(영어·큰 글자) 다음 줄로 — 글자가 한 글자씩 갈라지지 않게
        FlowRow(horizontalArrangement = Arrangement.spacedBy(ElsaSpacing.lg)) {
            ElsaCheckbox(
                checked = state.rememberId,
                onCheckedChange = viewModel::rememberIdChanged,
                label = stringResource(R.string.login_remember_id),
            )
            ElsaCheckbox(
                checked = state.autoLogin,
                onCheckedChange = viewModel::autoLoginChanged,
                label = stringResource(R.string.login_auto_login),
            )
        }

        state.error?.let { error ->
            ElsaInlineMessage(message = error.message(), type = ElsaMessageType.Error)
        }

        ElsaButton(
            text = stringResource(R.string.login_forgot_password),
            onClick = onPasswordResetClick,
            style = ElsaButtonStyle.Text,
            size = ElsaButtonSize.Small,
            fullWidth = false,
            modifier = Modifier.align(Alignment.End),
        )

        ElsaSocialLoginSection(
            providers = state.socialProviders,
            onClick = viewModel::socialLoginTapped,
            enabled = !state.isLoading,
        )
    }
    }
}
