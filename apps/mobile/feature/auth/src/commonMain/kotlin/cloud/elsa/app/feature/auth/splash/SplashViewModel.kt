package cloud.elsa.app.feature.auth.splash

import cloud.elsa.app.core.common.viewmodel.StatefulViewModel
import cloud.elsa.app.core.data.session.SessionRepository
import cloud.elsa.app.core.data.storage.setting.SettingStorage
import kotlinx.coroutines.flow.Flow

/** 앱 시작 시 저장된 Token/User로 세션을 복원합니다. */
class SplashViewModel(
    private val sessionRepository: SessionRepository,
    private val settingStorage: SettingStorage,
) : StatefulViewModel<SplashUiState, SplashEvent>(SplashUiState) {

    val events: Flow<SplashEvent> = eventFlow

    init {
        launch {
            val user = sessionRepository.restore()
            sendEvent(
                when {
                    user != null -> SplashEvent.SessionRestored(user)
                    !settingStorage.isOnboardingDone() -> SplashEvent.FirstLaunch
                    else -> SplashEvent.SessionNotFound
                },
            )
        }
    }

    /** 복원 중 예외가 나도 앱이 멈추지 않도록 비로그인으로 진행합니다. */
    override fun onError(throwable: Throwable) {
        trySendEvent(SplashEvent.SessionNotFound)
    }
}
