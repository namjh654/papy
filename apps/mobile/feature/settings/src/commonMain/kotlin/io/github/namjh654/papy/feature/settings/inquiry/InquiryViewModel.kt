package io.github.namjh654.papy.feature.settings.inquiry

import io.github.namjh654.papy.core.common.error.toAppError
import io.github.namjh654.papy.core.common.viewmodel.StatefulViewModel
import io.github.namjh654.papy.core.data.session.UserStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 문의하기(설정 → 고객 지원, 25-3). 사용자 생성 콘텐츠가 있는 앱은 앱 안에 연락 수단이 있어야 한다(App Store 1.2).
 * Swift: `IOSAppGraph.shared.inquiryViewModel()`
 */
class InquiryViewModel(
    private val repository: InquiryRepository,
    userStore: UserStore,
) : StatefulViewModel<InquiryUiState, InquiryEvent>(InquiryUiState(email = userStore.user.value?.email.orEmpty())) {

    val uiState: StateFlow<InquiryUiState> = state
    val events: Flow<InquiryEvent> = eventFlow

    fun categorySelected(category: InquiryCategory) = setState { copy(category = category, error = null) }

    fun contentChanged(value: String) = setState { copy(content = value, error = null) }

    fun emailChanged(value: String) = setState { copy(email = value, error = null) }

    fun submit() = launch {
        val state = currentState
        val category = state.category ?: return@launch
        if (!state.canSubmit) return@launch // 중복 전송 방지
        setState { copy(isSubmitting = true, error = null) }
        repository.submit(category, state.content, state.email)
        setState { copy(isSubmitting = false) }
        sendEvent(InquiryEvent.Submitted)
    }

    override fun onError(throwable: Throwable) = setState {
        copy(isSubmitting = false, error = throwable.toAppError().toUiError())
    }
}
