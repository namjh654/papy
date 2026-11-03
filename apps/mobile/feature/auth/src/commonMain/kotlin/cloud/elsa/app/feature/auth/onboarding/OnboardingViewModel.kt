package cloud.elsa.app.feature.auth.onboarding

import cloud.elsa.app.core.common.viewmodel.StatefulViewModel
import cloud.elsa.app.core.data.storage.setting.SettingStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 처음 실행할 때 한 번 보여주는 소개. 끝내거나 건너뛰면 "봤음"을 저장하고 로그인으로 간다.
 * 쪽은 밀어서도, 버튼으로도 넘어간다(화면의 pager 와 [pageChanged] 로 맞춘다).
 */
class OnboardingViewModel(
    private val settingStorage: SettingStorage,
) : StatefulViewModel<OnboardingUiState, OnboardingEvent>(OnboardingUiState()) {

    val uiState: StateFlow<OnboardingUiState> = state

    val events: Flow<OnboardingEvent> = eventFlow

    /** 사용자가 밀어서 쪽이 바뀌었을 때 */
    fun pageChanged(page: Int) = setState { copy(page = page.coerceIn(0, pageCount - 1)) }

    /** 아래 버튼: 다음 쪽, 마지막 쪽이면 시작 */
    fun next() {
        if (currentState.isLastPage) finish() else pageChanged(currentState.page + 1)
    }

    /** 뒤로가기: 앞 쪽으로(첫 쪽에서는 화면이 가로채지 않는다) */
    fun previous() = pageChanged(currentState.page - 1)

    fun skip() = finish()

    private fun finish() = launch {
        settingStorage.setOnboardingDone()
        sendEvent(OnboardingEvent.Done)
    }
}
