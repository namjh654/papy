package io.github.namjh654.papy.feature.auth.terms

import io.github.namjh654.papy.core.common.viewmodel.StatefulViewModel
import io.github.namjh654.papy.core.data.config.AppConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 회원가입 전 약관 동의. 필수 항목을 모두 동의하면 선택 항목(마케팅) 동의 여부를 들고 회원가입으로 간다.
 * 서버 호출이 없다 — 동의 값은 회원가입 요청에 실려 간다. 전문 보기(웹뷰)는 화면이 [TermsUiState.url] 로 연다.
 */
class TermsViewModel : StatefulViewModel<TermsUiState, TermsEvent>(
    TermsUiState(
        serviceUrl = AppConfig.URL_TERMS.ifBlank { null },
        privacyUrl = AppConfig.URL_PRIVACY.ifBlank { null },
    ),
) {

    val uiState: StateFlow<TermsUiState> = state

    val events: Flow<TermsEvent> = eventFlow

    fun toggle(agreement: Agreement) = setState {
        copy(checked = if (agreement in checked) checked - agreement else checked + agreement)
    }

    /** 전체 동의: 하나라도 빠져 있으면 모두 선택, 모두 선택돼 있으면 모두 해제 */
    fun toggleAll() = setState {
        copy(checked = if (allChecked) emptySet() else Agreement.entries.toSet())
    }

    fun continueClicked() = launch {
        if (currentState.canContinue) sendEvent(TermsEvent.Agreed(marketing = Agreement.Marketing in currentState.checked))
    }
}
