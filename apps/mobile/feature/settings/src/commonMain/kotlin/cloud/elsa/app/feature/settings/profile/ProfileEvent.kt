package cloud.elsa.app.feature.settings.profile

import cloud.elsa.app.core.common.viewmodel.UiEvent

sealed interface ProfileEvent : UiEvent {
    data object Saved : ProfileEvent
}
