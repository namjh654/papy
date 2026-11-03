package cloud.elsa.app.feature.auth.social

import cloud.elsa.app.core.common.viewmodel.UiEvent

sealed interface SocialConsentEvent : UiEvent {
    /** 동의를 저장했다 → 메인으로 */
    data object Completed : SocialConsentEvent

    /** 동의하지 않고 나갔다 → 로그아웃됨, 로그인 화면으로 */
    data object Cancelled : SocialConsentEvent
}
