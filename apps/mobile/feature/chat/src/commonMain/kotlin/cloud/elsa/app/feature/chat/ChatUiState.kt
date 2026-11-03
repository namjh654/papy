package cloud.elsa.app.feature.chat

import cloud.elsa.app.core.common.paging.ListState
import cloud.elsa.app.core.common.viewmodel.UiState

/** 대화 목록 화면: 목록 상태(로딩·새로고침·다음 페이지·오류·빈 화면)는 [ListState] 하나로 둔다 */
data class ChatUiState(
    val list: ListState<Conversation> = ListState(),
) : UiState
