package cloud.elsa.app.feature.example.detail

import cloud.elsa.app.core.common.error.toAppError
import cloud.elsa.app.core.common.viewmodel.StatefulViewModel
import cloud.elsa.app.feature.example.ExampleRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 상세 화면 기준 예시: id 로 한 건을 불러와 보여주고, 수정하면 목록에 반영된다.
 *
 * - 인자는 id 하나. Koin `parametersOf(id)` 로 받는다(Android `koinViewModel { parametersOf(id) }`, iOS `IOSAppGraph.exampleDetailViewModel(id:)`)
 * - 불러오기 실패 → 전체 오류 화면(다시 시도). 수정 실패 → 내용은 그대로, 이벤트로 알림
 * - 수정 결과는 [ExampleRepository.changes] 로 목록에 전달된다(여기서 목록을 알 필요 없음)
 */
class ExampleDetailViewModel(
    private val id: String,
    private val repository: ExampleRepository,
) : StatefulViewModel<ExampleDetailUiState, ExampleDetailEvent>(ExampleDetailUiState()) {

    val uiState: StateFlow<ExampleDetailUiState> = state

    val events: Flow<ExampleDetailEvent> = eventFlow

    init {
        load()
    }

    /** 진입 시, 그리고 전체 오류 화면의 다시 시도 */
    fun load() = launch {
        setState { copy(isLoading = true, error = null) }
        val item = repository.item(id)
        setState { copy(item = item, isLoading = false) }
    }

    fun toggleFavorite() = launch {
        val item = currentState.item ?: return@launch
        if (currentState.isSaving) return@launch // 중복 요청 방지
        setState { copy(isSaving = true) }
        try {
            val saved = repository.setFavorite(item.id, !item.favorite)
            setState { copy(item = saved, isSaving = false) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            setState { copy(isSaving = false) }
            sendEvent(ExampleDetailEvent.SaveFailed(e.toAppError().toUiError()))
        }
    }

    /** 불러오기 실패 → 전체 오류 화면 */
    override fun onError(throwable: Throwable) = setState {
        copy(isLoading = false, error = throwable.toAppError().toUiError())
    }
}
