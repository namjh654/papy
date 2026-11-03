package cloud.elsa.app.feature.inbox

import cloud.elsa.app.core.common.viewmodel.UiState
import cloud.elsa.app.core.common.paging.ListState

data class InboxUiState(
    val list: ListState<InboxItem> = ListState(),
) : UiState {
    /** "모두 읽음" 버튼을 보여줄지 */
    val hasUnread: Boolean
        get() = list.items.any { !it.read }
}
