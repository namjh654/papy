package io.github.namjh654.papy.feature.auth.login

import io.github.namjh654.papy.core.common.viewmodel.UiEvent
import io.github.namjh654.papy.core.data.session.UserModel
import io.github.namjh654.papy.core.data.social.SocialProvider

/**
 * LoginViewModel이 Native UI에 보내는 일회성 이벤트입니다.
 *
 * ViewModel은 "무슨 일이 일어났는지"만 알리고,
 * 어느 화면으로 이동할지는 Android/iOS Navigation이 결정합니다.
 */
sealed interface LoginEvent : UiEvent {
    data class LoginSucceeded(val user: UserModel) : LoginEvent

    /** 소셜 버튼을 눌렀다 → 플랫폼이 해당 SDK 로 인증하고 결과를 `socialCredentialReceived`·`socialSignInCancelled`·`socialSignInFailed` 로 돌려준다 */
    data class StartSocialSignIn(val provider: SocialProvider) : LoginEvent

    /** 소셜로 처음 가입했다(세션은 시작됨) → 화면이 약관 화면으로 보내고 결과를 `SocialConsentViewModel` 에 넘긴다(18-4) */
    data class SocialSignedUp(val user: UserModel) : LoginEvent

    /** 회원가입·비밀번호 재설정을 마치고 돌아왔다 → 화면이 토스트로 안내한다 */
    data class Notice(val notice: LoginNotice) : LoginEvent
}
