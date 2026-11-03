package io.github.namjh654.papy.feature.settings.profile

import io.github.namjh654.papy.core.common.viewmodel.UiEvent

sealed interface ProfileEvent : UiEvent {
    data object Saved : ProfileEvent
}
