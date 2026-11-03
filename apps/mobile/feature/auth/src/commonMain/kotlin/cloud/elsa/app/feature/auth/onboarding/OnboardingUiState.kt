package cloud.elsa.app.feature.auth.onboarding

import cloud.elsa.app.core.common.viewmodel.UiState

/**
 * @property page 지금 보는 쪽(0 부터)
 * @property pageCount 쪽 수. 쪽의 내용(제목·설명·아이콘)은 화면이 쪽 번호로 정한다
 */
data class OnboardingUiState(
    val page: Int = 0,
    val pageCount: Int = PAGE_COUNT,
) : UiState {
    val isLastPage: Boolean
        get() = page == pageCount - 1

    companion object {
        const val PAGE_COUNT = 3
    }
}
