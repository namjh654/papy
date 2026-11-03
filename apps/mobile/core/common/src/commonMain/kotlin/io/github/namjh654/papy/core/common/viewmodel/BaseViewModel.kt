package io.github.namjh654.papy.core.common.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 모든 공유 ViewModel 의 최상위 베이스(비제네릭 — Swift `ViewModelOwner<VM: BaseViewModel>` 제약용).
 * 화면 ViewModel 은 보통 [StatefulViewModel] 을 상속한다.
 */
abstract class BaseViewModel : ViewModel() {

    private val exceptionHandler =
        CoroutineExceptionHandler { _, throwable ->

            if (throwable !is CancellationException) {
                onError(throwable)
            }
        }

    protected fun launch(
        block: suspend CoroutineScope.() -> Unit,
    ) {
        viewModelScope.launch(
            context = exceptionHandler,
            block = block,
        )
    }

    protected open fun onError(
        throwable: Throwable,
    ) = Unit
}