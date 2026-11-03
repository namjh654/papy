package io.github.namjh654.papy.core.common.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import kotlin.reflect.KClass

/**
 * iOS에는 ViewModelStoreOwner가 없으므로 화면이 사라질 때 Swift에서 직접 호출해
 * onCleared()와 viewModelScope 취소를 수행합니다.
 */
fun BaseViewModel.dispose() {
    val viewModel = this
    val store = ViewModelStore()

    ViewModelProvider.create(
        store = store,
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: KClass<T>,
                extras: CreationExtras,
            ): T = viewModel as T
        },
    )[viewModel::class]

    store.clear()
}
