package cloud.elsa.app.feature.auth.splash

import cloud.elsa.app.core.common.viewmodel.UiEvent
import cloud.elsa.app.core.data.session.UserModel

/** 세션 복원 결과입니다. Native UI는 이 결과로 시작 화면을 결정합니다. */
sealed interface SplashEvent : UiEvent {
    data class SessionRestored(val user: UserModel) : SplashEvent
    data object SessionNotFound : SplashEvent

    /** 처음 실행(온보딩을 아직 보지 않음) */
    data object FirstLaunch : SplashEvent
}
