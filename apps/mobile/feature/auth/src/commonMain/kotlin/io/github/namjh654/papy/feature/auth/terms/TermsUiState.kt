package io.github.namjh654.papy.feature.auth.terms

import io.github.namjh654.papy.core.common.viewmodel.UiState

/** 동의 항목. 항목을 늘리면 화면의 제목 매핑(TermsScreen)과 [TermsUiState.url] 을 같이 고친다 */
enum class Agreement(val required: Boolean) {
    Service(required = true),
    Privacy(required = true),
    Marketing(required = false);

    /** Swift 에서는 `required` 가 예약어라 이 이름으로 읽는다 */
    val isRequired: Boolean get() = required
}

/**
 * @property serviceUrl·privacyUrl 전문 보기 주소(env `url.terms`·`url.privacy`). 비어 있으면 "보기"를 숨긴다
 */
data class TermsUiState(
    val checked: Set<Agreement> = emptySet(),
    val serviceUrl: String? = null,
    val privacyUrl: String? = null,
) : UiState {
    val allChecked: Boolean
        get() = checked.size == Agreement.entries.size

    /** 필수 항목을 모두 동의해야 다음으로 간다 */
    val canContinue: Boolean
        get() = Agreement.entries.filter { it.required }.all { it in checked }

    fun isChecked(agreement: Agreement): Boolean = agreement in checked

    fun url(agreement: Agreement): String? = when (agreement) {
        Agreement.Service -> serviceUrl
        Agreement.Privacy -> privacyUrl
        Agreement.Marketing -> null
    }
}
