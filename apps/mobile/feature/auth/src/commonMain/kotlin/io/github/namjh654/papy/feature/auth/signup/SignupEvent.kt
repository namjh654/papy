package io.github.namjh654.papy.feature.auth.signup

import io.github.namjh654.papy.core.common.viewmodel.UiEvent

/**
 * SignupViewModel이 Native UI에 보내는 일회성 이벤트입니다.
 * 가입 성공 후 로그인 화면 복귀는 각 플랫폼 Navigation이 처리합니다.
 */
sealed interface SignupEvent : UiEvent {
    /** 완료 문구는 Native UI 리소스가 결정한다(서버 message 는 표시하지 않음). */
    data class SignupSucceeded(
        val username: String,
    ) : SignupEvent
}
