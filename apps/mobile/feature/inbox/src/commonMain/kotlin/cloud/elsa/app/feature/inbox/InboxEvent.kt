package cloud.elsa.app.feature.inbox

import cloud.elsa.app.core.common.viewmodel.UiEvent

sealed interface InboxEvent : UiEvent {
    /** 알림에 연결된 화면 열기(딥링크와 같은 형식). 화면이 `PushManager.openLink(link)` 로 넘긴다 */
    data class Open(val link: String) : InboxEvent
}
