package io.github.namjh654.papy.feature.auth.reset

import io.github.namjh654.papy.core.common.viewmodel.UiEvent

sealed interface PasswordResetEvent : UiEvent {
    /** "코드 다시 받기" 성공 */
    data object CodeResent : PasswordResetEvent

    /** 비밀번호를 바꿨다 → 로그인 화면으로 돌아간다(안내는 LoginNoticeStore 가 전달) */
    data object Done : PasswordResetEvent
}
