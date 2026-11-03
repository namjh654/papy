package io.github.namjh654.papy.feature.auth.onboarding

import io.github.namjh654.papy.core.common.viewmodel.UiEvent

sealed interface OnboardingEvent : UiEvent {
    /** 끝까지 봤거나 건너뛰었다 → 로그인 화면으로 */
    data object Done : OnboardingEvent
}
