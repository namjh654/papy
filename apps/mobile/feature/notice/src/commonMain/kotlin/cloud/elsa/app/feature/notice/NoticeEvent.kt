package cloud.elsa.app.feature.notice

import cloud.elsa.app.core.common.viewmodel.UiEvent

/** 화면 이동은 플랫폼 Navigation 이 결정한다. ViewModel 은 무슨 일이 있었는지만 알린다. */
sealed interface NoticeEvent : UiEvent {
    /** 상세 화면으로(id 만 넘긴다) */
    data class ItemSelected(val id: String) : NoticeEvent
}
