package cloud.elsa.app.feature.settings.inquiry

import cloud.elsa.app.core.common.viewmodel.UiEvent

sealed interface InquiryEvent : UiEvent {
    /** 보냈다 → 안내 후 이전 화면으로 */
    data object Submitted : InquiryEvent
}
