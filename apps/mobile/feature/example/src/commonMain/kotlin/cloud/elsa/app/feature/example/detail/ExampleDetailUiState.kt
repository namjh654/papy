package cloud.elsa.app.feature.example.detail

import cloud.elsa.app.core.common.error.UiError
import cloud.elsa.app.core.common.viewmodel.UiState
import cloud.elsa.app.feature.example.ExampleItem

/**
 * 상세 화면 상태. [item] 이 없으면 전체 로딩 또는 전체 오류(다시 시도), 있으면 내용을 보여준다.
 *
 * @property isSaving 수정 요청 중(버튼 로딩, 중복 요청 방지)
 */
data class ExampleDetailUiState(
    val item: ExampleItem? = null,
    val isLoading: Boolean = true,
    val error: UiError? = null,
    val isSaving: Boolean = false,
) : UiState
