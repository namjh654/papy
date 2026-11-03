package io.github.namjh654.papy.feature.settings.inquiry

import io.github.namjh654.papy.core.common.viewmodel.UiEvent

sealed interface InquiryEvent : UiEvent {
    /** 보냈다 → 안내 후 이전 화면으로 */
    data object Submitted : InquiryEvent
}
