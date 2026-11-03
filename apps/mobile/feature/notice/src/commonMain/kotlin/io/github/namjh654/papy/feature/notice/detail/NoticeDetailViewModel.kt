package io.github.namjh654.papy.feature.notice.detail

import io.github.namjh654.papy.core.common.error.toAppError
import io.github.namjh654.papy.core.common.viewmodel.StatefulViewModel
import io.github.namjh654.papy.core.common.viewmodel.UiEvent
import io.github.namjh654.papy.feature.notice.NoticeRepository
import kotlinx.coroutines.flow.StateFlow

/** 일회성 이벤트가 없는 화면 */
sealed interface NoticeDetailEvent : UiEvent

/**
 * 공지 상세(읽기 전용). id 로 불러온다 — 목록에서 눌러도, 푸시·링크(`notice/<id>`)로 와도 같은 경로.
 * 수정이 있는 상세는 feature:example 의 ExampleDetailViewModel 을 본다.
 */
class NoticeDetailViewModel(
    private val id: String,
    private val repository: NoticeRepository,
) : StatefulViewModel<NoticeDetailUiState, NoticeDetailEvent>(NoticeDetailUiState()) {

    val uiState: StateFlow<NoticeDetailUiState> = state

    init {
        load()
    }

    /** 진입 시, 그리고 전체 오류 화면의 다시 시도 */
    fun load() = launch {
        setState { copy(isLoading = true, error = null) }
        val item = repository.item(id)
        setState { copy(item = item, isLoading = false) }
    }

    override fun onError(throwable: Throwable) = setState {
        copy(isLoading = false, error = throwable.toAppError().toUiError())
    }
}
