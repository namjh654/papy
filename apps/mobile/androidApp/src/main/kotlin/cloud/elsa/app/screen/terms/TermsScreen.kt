package cloud.elsa.app.screen.terms

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
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
import cloud.elsa.app.designsystem.component.ElsaSettingsDivider
import cloud.elsa.app.designsystem.theme.ElsaTheme
import cloud.elsa.app.designsystem.token.ElsaSpacing
import cloud.elsa.app.feature.auth.terms.Agreement
import cloud.elsa.app.feature.auth.terms.TermsEvent
import cloud.elsa.app.feature.auth.social.SocialConsentEvent
import cloud.elsa.app.feature.auth.social.SocialConsentViewModel
import cloud.elsa.app.feature.auth.terms.TermsUiState
import cloud.elsa.app.feature.auth.terms.TermsViewModel
import org.koin.androidx.compose.koinViewModel

/**
 * 약관 동의(회원가입 전): 전체 동의 + 항목별 체크(필수·선택 표시, 전문 보기), 하단 고정 버튼.
 * @param onAgreed 선택 동의(마케팅) 값을 들고 회원가입으로
 */
@Composable
fun TermsScreen(
    onAgreed: (marketing: Boolean) -> Unit,
    onOpenWeb: (url: String, title: String) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: TermsViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    EventEffect(viewModel.events) { event ->
        when (event) {
            is TermsEvent.Agreed -> onAgreed(event.marketing)
        }
    }

    TermsContent(
        state = state,
        onToggleAll = viewModel::toggleAll,
        onToggle = viewModel::toggle,
        onContinue = viewModel::continueClicked,
        onOpenWeb = onOpenWeb,
        onBack = onBack,
    )
}

/**
 * 처음 가입한 소셜 사용자(18-4): 같은 약관 화면에서 동의 → 서버에 기록 → 메인.
 * 뒤로(버튼·시스템)는 가입을 마치지 않은 것이므로 로그아웃하고 로그인으로 돌아간다.
 */
@Composable
fun SocialConsentScreen(
    onCompleted: () -> Unit,
    onCancelled: () -> Unit,
    onOpenWeb: (url: String, title: String) -> Unit,
) {
    val termsViewModel: TermsViewModel = koinViewModel()
    val consentViewModel: SocialConsentViewModel = koinViewModel()
    val state by termsViewModel.uiState.collectAsStateWithLifecycle()
    val consent by consentViewModel.uiState.collectAsStateWithLifecycle()

    EventEffect(termsViewModel.events) { event ->
        when (event) {
            is TermsEvent.Agreed -> consentViewModel.agree(event.marketing)
        }
    }
    EventEffect(consentViewModel.events) { event ->
        when (event) {
            SocialConsentEvent.Completed -> onCompleted()
            SocialConsentEvent.Cancelled -> onCancelled()
        }
    }
    BackHandler(onBack = consentViewModel::cancel)

    TermsContent(
        state = state,
        onToggleAll = termsViewModel::toggleAll,
        onToggle = termsViewModel::toggle,
        onContinue = termsViewModel::continueClicked,
        onOpenWeb = onOpenWeb,
        onBack = consentViewModel::cancel,
        isLoading = consent.isLoading,
        error = consent.error?.message(),
    )
}

/** 상태·콜백만 받는 본문(미리보기 대상, DEC-122) */
@Composable
fun TermsContent(
    state: TermsUiState,
    onToggleAll: () -> Unit,
    onToggle: (Agreement) -> Unit,
    onContinue: () -> Unit,
    onOpenWeb: (url: String, title: String) -> Unit,
    onBack: () -> Unit,
    isLoading: Boolean = false,
    error: String? = null,
) {
    ElsaScaffold(onBack = onBack) { padding ->
        ElsaFormColumn(
            modifier = Modifier.padding(padding),
            bottomBar = {
                ElsaButton(
                    text = stringResource(R.string.terms_continue),
                    onClick = onContinue,
                    enabled = state.canContinue || isLoading,
                    isLoading = isLoading,
                )
            },
        ) {
            ElsaScreenHeader(title = stringResource(R.string.terms_title))

            // 화면 배경 위에 바로 둔다 — 같은 색 카드는 보이지 않고 들여쓰기만 생긴다(제목과 왼쪽 정렬)
            Column(verticalArrangement = Arrangement.spacedBy(ElsaSpacing.md)) {
                ElsaCheckbox(
                    checked = state.allChecked,
                    onCheckedChange = { onToggleAll() },
                    label = stringResource(R.string.terms_all),
                )
                ElsaSettingsDivider()
                Agreement.entries.forEach { agreement ->
                    val title = agreement.title()
                    val url = state.url(agreement)
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        ElsaCheckbox(
                            checked = state.isChecked(agreement),
                            onCheckedChange = { onToggle(agreement) },
                            label = title,
                            description = stringResource(
                                if (agreement.required) R.string.terms_required else R.string.terms_optional,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        if (url != null) {
                            ElsaButton(
                                text = stringResource(R.string.terms_view),
                                onClick = { onOpenWeb(url, title) },
                                style = ElsaButtonStyle.Text,
                                size = ElsaButtonSize.Small,
                                fullWidth = false,
                            )
                        }
                    }
                }
            }

            error?.let { ElsaInlineMessage(message = it, type = ElsaMessageType.Error) }
        }
    }
}

@Composable
private fun Agreement.title(): String = stringResource(
    when (this) {
        Agreement.Service -> R.string.settings_terms
        Agreement.Privacy -> R.string.settings_privacy
        Agreement.Marketing -> R.string.terms_marketing
    },
)

@Preview(name = "라이트")
@Preview(name = "다크", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TermsContentPreview() = ElsaTheme {
    TermsContent(
        state = TermsUiState(checked = setOf(Agreement.Service), serviceUrl = "https://example.com"),
        onToggleAll = {}, onToggle = {}, onContinue = {}, onOpenWeb = { _, _ -> }, onBack = {},
    )
}
