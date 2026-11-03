package io.github.namjh654.papy.feature.community.editor

import io.github.namjh654.papy.core.common.viewmodel.UiEvent
import io.github.namjh654.papy.feature.community.Post

sealed interface PostEditorEvent : UiEvent {
    /** 저장했다 — 닫는다(목록·상세는 PostChange 로 반영) */
    data class Saved(val post: Post) : PostEditorEvent
}
