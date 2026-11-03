package cloud.elsa.app.feature.auth.onboarding

import cloud.elsa.app.core.common.viewmodel.UiEvent

sealed interface OnboardingEvent : UiEvent {
    /** 끝까지 봤거나 건너뛰었다 → 로그인 화면으로 */
    data object Done : OnboardingEvent
}
