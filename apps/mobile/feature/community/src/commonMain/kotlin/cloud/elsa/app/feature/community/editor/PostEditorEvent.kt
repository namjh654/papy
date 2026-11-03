package cloud.elsa.app.feature.community.editor

import cloud.elsa.app.core.common.viewmodel.UiEvent
import cloud.elsa.app.feature.community.Post

sealed interface PostEditorEvent : UiEvent {
    /** 저장했다 — 닫는다(목록·상세는 PostChange 로 반영) */
    data class Saved(val post: Post) : PostEditorEvent
}
