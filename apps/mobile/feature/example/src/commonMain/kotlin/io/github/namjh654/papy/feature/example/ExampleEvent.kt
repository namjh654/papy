package io.github.namjh654.papy.feature.example

import io.github.namjh654.papy.core.common.viewmodel.UiEvent

/** 화면 이동은 플랫폼 Navigation 이 결정한다. ViewModel 은 무슨 일이 있었는지만 알린다. */
sealed interface ExampleEvent : UiEvent {
    /** 상세 화면으로. 인자는 id 만 넘긴다(상세가 직접 불러온다 → 딥링크와 같은 경로) */
    data class ItemSelected(val id: String) : ExampleEvent
}
