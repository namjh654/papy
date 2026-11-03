package cloud.elsa.app.feature.auth.terms

import cloud.elsa.app.core.common.viewmodel.UiEvent

sealed interface TermsEvent : UiEvent {
    /** 필수 항목에 모두 동의했다 → 회원가입으로(선택 동의 값을 넘긴다) */
    data class Agreed(val marketing: Boolean) : TermsEvent
}
