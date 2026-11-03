package io.github.namjh654.papy.screen.chat

import android.content.res.Configuration
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.namjh654.papy.R
import io.github.namjh654.papy.component.EventEffect
import io.github.namjh654.papy.designsystem.component.PapyAvatar
import io.github.namjh654.papy.designsystem.component.PapyListItem
import io.github.namjh654.papy.designsystem.component.PapyPagedList
import io.github.namjh654.papy.designsystem.component.PapyScaffold
import io.github.namjh654.papy.designsystem.component.PapyScreenHeader
import io.github.namjh654.papy.designsystem.component.PapySkeletonRow
import io.github.namjh654.papy.designsystem.format.PapyFormat
import io.github.namjh654.papy.designsystem.format.currentLocale
import io.github.namjh654.papy.designsystem.theme.PapyTheme
import io.github.namjh654.papy.designsystem.token.PapySize
import io.github.namjh654.papy.designsystem.token.PapySpacing
import io.github.namjh654.papy.feature.chat.ChatEvent
import io.github.namjh654.papy.feature.chat.ChatViewModel
import io.github.namjh654.papy.feature.chat.Conversation
import org.koin.androidx.compose.koinViewModel

/**
 * 대화 목록(메시지 탭): 큰 제목, 대화 한 줄 = 아바타·이름·마지막 메시지·시간·안 읽은 수(텔레그램 목록과 같은 구성).
 * @param onBack 탭 루트면 null(뒤로가기 없음), 다른 화면 위에 열리면 뒤로가기
 */
@Composable
fun ChatScreen(
    onItemSelected: (Conversation) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val viewModel: ChatViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    EventEffect(viewModel.events) { event ->
        when (event) {
            is ChatEvent.ConversationSelected -> onItemSelected(event.conversation)
        }
    }

    PapyScaffold(onBack = onBack) { padding ->
        PapyPagedList(
            state = state.list,
            key = { it.id },
            emptyTitle = stringResource(R.string.chat_empty),
            onRefresh = viewModel::refresh,
            onLoadMore = viewModel::loadMore,
            onRetry = viewModel::load,
            onRetryLoadMore = viewModel::retryLoadMore,
            modifier = Modifier.padding(padding),
            // 대화 줄은 화면 끝까지 — 줄 안에 좌우 여백이 있다
            contentPadding = PaddingValues(bottom = PapySize.screenPadding),
            loadingContent = { Column { repeat(6) { PapySkeletonRow(avatar = 52.dp) } } },
            header = {
                item(key = "header") {
                    PapyScreenHeader(
                        title = stringResource(R.string.chat_title),
                        modifier = Modifier.padding(horizontal = PapySize.screenPadding).padding(top = PapySize.screenPadding),
                    )
                }
            },
        ) { conversation ->
            ConversationRow(conversation = conversation, onClick = { viewModel.conversationClicked(conversation) })
        }
    }
}

/** 대화 한 줄(미리보기 대상, DEC-122) */
@Composable
fun ConversationRow(conversation: Conversation, onClick: () -> Unit) {
    PapyListItem(
        title = conversation.title,
        subtitle = conversation.lastMessage,
        leading = { PapyAvatar(name = conversation.title, url = conversation.avatarUrl, size = 52.dp) },
        trailing = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(PapySpacing.xs)) {
                conversation.lastMessageAt?.let {
                    Text(text = conversationTime(it), style = PapyTheme.typography.caption, color = PapyTheme.colors.onSurfaceTertiary)
                }
                if (conversation.unreadCount > 0) UnreadBadge(conversation.unreadCount)
            }
        },
        onClick = onClick,
    )
}

/** 오늘이면 시각, 아니면 날짜 */
@Composable
private fun conversationTime(millis: Long): String {
    val locale = currentLocale
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    return if (DateUtils.isToday(millis)) PapyFormat.time(millis, locale, is24Hour) else PapyFormat.date(millis, locale)
}

@Composable
private fun UnreadBadge(count: Int) {
    Text(
        text = if (count > 99) "99+" else "$count",
        style = PapyTheme.typography.caption,
        color = PapyTheme.colors.onPrimary,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .clip(CircleShape)
            .background(PapyTheme.colors.primary)
            .defaultMinSize(minWidth = 20.dp)
            .padding(horizontal = PapySpacing.xs, vertical = PapySpacing.xxs),
    )
}

@Preview(name = "라이트")
@Preview(name = "다크", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ConversationRowPreview() = PapyTheme {
    Column(modifier = Modifier.background(PapyTheme.colors.background)) {
        ConversationRow(Conversation(id = "1", title = "엘사", lastMessage = "내일 봬요!", lastMessageAt = System.currentTimeMillis(), unreadCount = 2)) {}
        ConversationRow(Conversation(id = "2", title = "디자인팀", lastMessage = "새 아이콘을 올렸어요.", lastMessageAt = 0)) {}
    }
}
