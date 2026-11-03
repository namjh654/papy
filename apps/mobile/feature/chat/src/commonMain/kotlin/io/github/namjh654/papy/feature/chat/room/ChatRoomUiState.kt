package io.github.namjh654.papy.feature.chat.room

import io.github.namjh654.papy.core.common.error.UiError
import io.github.namjh654.papy.core.common.viewmodel.UiState
import io.github.namjh654.papy.core.data.files.DownloadState
import io.github.namjh654.papy.feature.chat.Message

/** Swift: `SendState.sending` 등 (SKIE enum) */
enum class SendState { Sending, Sent, Failed }

/**
 * 화면에 그리는 메시지. [key] 는 낙관적 전송 말풍선이 서버 메시지로 바뀌어도 그대로라 목록 위치·미리보기를 유지한다.
 * [isRead]: 내 메시지를 상대가 읽었는지.
 */
data class ChatMessage(
    val message: Message,
    val isMine: Boolean,
    val sendState: SendState = SendState.Sent,
    val isRead: Boolean = false,
    /** 답장한 원문이 내 메시지인지(인용 블록의 이름 — "나" 또는 상대) */
    val replyIsMine: Boolean = false,
) {
    val key: String get() = message.clientId ?: message.id
}

/** 연속 메시지 묶음 안의 위치 — 말풍선 모서리·간격·꼬리(마지막)를 정한다. Swift: `BubblePosition.single` 등 */
enum class BubblePosition { Single, First, Middle, Last }

/** 대화방 목록의 한 줄(오래된 것 → 새 것 순서). Swift: `onEnum(of: row)` */
sealed interface ChatRow {
    val key: String

    /** 날짜 구분선. [dayStartMillis]: 기기 시간대 그날 0시(epoch 밀리초) — 문구는 플랫폼이 날짜 형식으로 만든다 */
    data class DateHeader(val dayStartMillis: Long) : ChatRow {
        override val key: String get() = "date-$dayStartMillis"
    }

    /** [showTime]: 묶음의 마지막 말풍선에만 시간·읽음을 붙인다 */
    data class Bubble(
        val item: ChatMessage,
        val position: BubblePosition,
        val showTime: Boolean,
    ) : ChatRow {
        override val key: String get() = item.key
    }
}

/**
 * 대화방 화면 상태.
 * - [rows]: 날짜 구분선 + 말풍선(오래된 것 → 새 것). Android 는 뒤집어서 아래부터 쌓고(reverseLayout), iOS 는 아래에 붙여 그린다
 * - [isLoadingOlder]·[hasOlder]: 맨 위에서 이전 메시지 불러오기
 * - [error]: 첫 불러오기 실패(전체 오류 화면). 보내기 실패는 말풍선의 [SendState.Failed] 로만 보인다
 */
data class ChatRoomUiState(
    val title: String = "",
    val rows: List<ChatRow> = emptyList(),
    val input: String = "",
    val isLoading: Boolean = true,
    val isLoadingOlder: Boolean = false,
    val hasOlder: Boolean = false,
    val error: UiError? = null,
    /** 답장할 원문(입력창 위 막대, 22-1) */
    val replyingTo: ChatMessage? = null,
    /** 상대가 입력 중(점 3개) */
    val isPeerTyping: Boolean = false,
    /** 대화방 검색(위 검색창·결과 목록) */
    val isSearchOpen: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<Message> = emptyList(),
    val isSearching: Boolean = false,
    /** 첨부 버튼(서버가 끌 수 있다 — FeatureKey.ChatAttachments, 28-2) */
    val attachmentsEnabled: Boolean = true,
    /** 파일 첨부: 받는 중·실패(주소 → 상태), 받아 둔 주소(누르면 바로 연다) — 30-1 */
    val fileStates: Map<String, DownloadState> = emptyMap(),
    val downloadedUrls: Set<String> = emptySet(),
) : UiState {
    val canSend: Boolean get() = input.isNotBlank()
}
