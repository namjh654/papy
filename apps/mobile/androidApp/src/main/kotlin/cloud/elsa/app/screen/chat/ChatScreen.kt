package cloud.elsa.app.screen.chat

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
import cloud.elsa.app.R
import cloud.elsa.app.component.EventEffect
import cloud.elsa.app.designsystem.component.ElsaAvatar
import cloud.elsa.app.designsystem.component.ElsaListItem
import cloud.elsa.app.designsystem.component.ElsaPagedList
import cloud.elsa.app.designsystem.component.ElsaScaffold
import cloud.elsa.app.designsystem.component.ElsaScreenHeader
import cloud.elsa.app.designsystem.component.ElsaSkeletonRow
import cloud.elsa.app.designsystem.format.ElsaFormat
import cloud.elsa.app.designsystem.format.currentLocale
import cloud.elsa.app.designsystem.theme.ElsaTheme
import cloud.elsa.app.designsystem.token.ElsaSize
import cloud.elsa.app.designsystem.token.ElsaSpacing
import cloud.elsa.app.feature.chat.ChatEvent
import cloud.elsa.app.feature.chat.ChatViewModel
import cloud.elsa.app.feature.chat.Conversation
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

    ElsaScaffold(onBack = onBack) { padding ->
        ElsaPagedList(
            state = state.list,
            key = { it.id },
            emptyTitle = stringResource(R.string.chat_empty),
            onRefresh = viewModel::refresh,
            onLoadMore = viewModel::loadMore,
            onRetry = viewModel::load,
            onRetryLoadMore = viewModel::retryLoadMore,
            modifier = Modifier.padding(padding),
            // 대화 줄은 화면 끝까지 — 줄 안에 좌우 여백이 있다
            contentPadding = PaddingValues(bottom = ElsaSize.screenPadding),
            loadingContent = { Column { repeat(6) { ElsaSkeletonRow(avatar = 52.dp) } } },
            header = {
                item(key = "header") {
                    ElsaScreenHeader(
                        title = stringResource(R.string.chat_title),
                        modifier = Modifier.padding(horizontal = ElsaSize.screenPadding).padding(top = ElsaSize.screenPadding),
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
    ElsaListItem(
        title = conversation.title,
        subtitle = conversation.lastMessage,
        leading = { ElsaAvatar(name = conversation.title, url = conversation.avatarUrl, size = 52.dp) },
        trailing = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(ElsaSpacing.xs)) {
                conversation.lastMessageAt?.let {
                    Text(text = conversationTime(it), style = ElsaTheme.typography.caption, color = ElsaTheme.colors.onSurfaceTertiary)
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
    return if (DateUtils.isToday(millis)) ElsaFormat.time(millis, locale, is24Hour) else ElsaFormat.date(millis, locale)
}

@Composable
private fun UnreadBadge(count: Int) {
    Text(
        text = if (count > 99) "99+" else "$count",
        style = ElsaTheme.typography.caption,
        color = ElsaTheme.colors.onPrimary,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .clip(CircleShape)
            .background(ElsaTheme.colors.primary)
            .defaultMinSize(minWidth = 20.dp)
            .padding(horizontal = ElsaSpacing.xs, vertical = ElsaSpacing.xxs),
    )
}

@Preview(name = "라이트")
@Preview(name = "다크", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ConversationRowPreview() = ElsaTheme {
    Column(modifier = Modifier.background(ElsaTheme.colors.background)) {
        ConversationRow(Conversation(id = "1", title = "엘사", lastMessage = "내일 봬요!", lastMessageAt = System.currentTimeMillis(), unreadCount = 2)) {}
        ConversationRow(Conversation(id = "2", title = "디자인팀", lastMessage = "새 아이콘을 올렸어요.", lastMessageAt = 0)) {}
    }
}
