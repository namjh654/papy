package io.github.namjh654.papy.feature.chat

import io.github.namjh654.papy.core.common.viewmodel.UiEvent

/** 화면 이동은 플랫폼 Navigation 이 결정한다. ViewModel 은 무슨 일이 있었는지만 알린다. */
sealed interface ChatEvent : UiEvent {
    /** 대화방으로 */
    data class ConversationSelected(val conversation: Conversation) : ChatEvent
}
