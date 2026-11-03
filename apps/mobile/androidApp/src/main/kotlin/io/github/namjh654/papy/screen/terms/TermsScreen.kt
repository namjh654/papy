package io.github.namjh654.papy.screen.terms

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
import io.github.namjh654.papy.designsystem.component.PapySettingsDivider
import io.github.namjh654.papy.designsystem.theme.PapyTheme
import io.github.namjh654.papy.designsystem.token.PapySpacing
import io.github.namjh654.papy.feature.auth.terms.Agreement
import io.github.namjh654.papy.feature.auth.terms.TermsEvent
import io.github.namjh654.papy.feature.auth.social.SocialConsentEvent
import io.github.namjh654.papy.feature.auth.social.SocialConsentViewModel
import io.github.namjh654.papy.feature.auth.terms.TermsUiState
import io.github.namjh654.papy.feature.auth.terms.TermsViewModel
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
    PapyScaffold(onBack = onBack) { padding ->
        PapyFormColumn(
            modifier = Modifier.padding(padding),
            bottomBar = {
                PapyButton(
                    text = stringResource(R.string.terms_continue),
                    onClick = onContinue,
                    enabled = state.canContinue || isLoading,
                    isLoading = isLoading,
                )
            },
        ) {
            PapyScreenHeader(title = stringResource(R.string.terms_title))

            // 화면 배경 위에 바로 둔다 — 같은 색 카드는 보이지 않고 들여쓰기만 생긴다(제목과 왼쪽 정렬)
            Column(verticalArrangement = Arrangement.spacedBy(PapySpacing.md)) {
                PapyCheckbox(
                    checked = state.allChecked,
                    onCheckedChange = { onToggleAll() },
                    label = stringResource(R.string.terms_all),
                )
                PapySettingsDivider()
                Agreement.entries.forEach { agreement ->
                    val title = agreement.title()
                    val url = state.url(agreement)
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        PapyCheckbox(
                            checked = state.isChecked(agreement),
                            onCheckedChange = { onToggle(agreement) },
                            label = title,
                            description = stringResource(
                                if (agreement.required) R.string.terms_required else R.string.terms_optional,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        if (url != null) {
                            PapyButton(
                                text = stringResource(R.string.terms_view),
                                onClick = { onOpenWeb(url, title) },
                                style = PapyButtonStyle.Text,
                                size = PapyButtonSize.Small,
                                fullWidth = false,
                            )
                        }
                    }
                }
            }

            error?.let { PapyInlineMessage(message = it, type = PapyMessageType.Error) }
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
private fun TermsContentPreview() = PapyTheme {
    TermsContent(
        state = TermsUiState(checked = setOf(Agreement.Service), serviceUrl = "https://example.com"),
        onToggleAll = {}, onToggle = {}, onContinue = {}, onOpenWeb = { _, _ -> }, onBack = {},
    )
}
