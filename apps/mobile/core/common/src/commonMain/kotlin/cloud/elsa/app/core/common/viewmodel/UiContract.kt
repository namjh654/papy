package cloud.elsa.app.core.common.viewmodel

/**
 * 화면 상태 계약. 화면마다 `data class XUiState(...) : UiState` 하나를 둔다.
 * 상태가 필요 없는 화면은 `data object XUiState : UiState`.
 */
interface UiState

/**
 * 화면이 한 번만 처리하는 이벤트(화면 이동, 토스트 등) 계약.
 * 화면마다 `sealed interface XEvent : UiEvent` 로 두면 Swift 에서 `onEnum(of:)` 로 분기한다.
 */
interface UiEvent
