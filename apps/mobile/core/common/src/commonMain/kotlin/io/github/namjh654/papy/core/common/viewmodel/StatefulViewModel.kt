package io.github.namjh654.papy.core.common.viewmodel

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

/**
 * UiState(StateFlow) + UiEvent(일회성) 를 가진 화면 ViewModel 베이스.
 *
 * 하위 클래스는 공개 프로퍼티를 **구체 타입으로** 한 줄씩 노출한다.
 * ```
 * val uiState: StateFlow<LoginUiState> = state
 * val events: Flow<LoginEvent> = eventFlow
 * ```
 * (제네릭 프로퍼티를 그대로 공개하면 Swift 에서 SKIE 가 구체 타입을 알 수 없다.)
 */
abstract class StatefulViewModel<S : UiState, E : UiEvent>(
    initialState: S,
) : BaseViewModel() {

    private val _state = MutableStateFlow(initialState)
    private val _events = Channel<E>(Channel.BUFFERED)

    protected val state: StateFlow<S> = _state.asStateFlow()
    protected val eventFlow: Flow<E> = _events.receiveAsFlow()

    protected val currentState: S
        get() = _state.value

    protected fun setState(reduce: S.() -> S) {
        _state.update(reduce)
    }

    protected suspend fun sendEvent(event: E) {
        _events.send(event)
    }

    /** 코루틴 밖(onError 등)에서 보낼 때 */
    protected fun trySendEvent(event: E) {
        _events.trySend(event)
    }
}
