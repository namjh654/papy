package io.github.namjh654.papy.designsystem.component

import android.content.res.Configuration
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.namjh654.papy.R
import io.github.namjh654.papy.designsystem.theme.PapyTheme
import io.github.namjh654.papy.designsystem.token.PapyRadius
import io.github.namjh654.papy.designsystem.token.PapySize
import io.github.namjh654.papy.designsystem.token.PapySpacing
import io.github.namjh654.papy.feature.chat.room.BubblePosition
import io.github.namjh654.papy.feature.chat.room.SendState
import coil3.compose.AsyncImage

// 메시지(채팅) UI — 화면 구성·동작은 텔레그램처럼, 코드·이름·로고는 쓰지 않는다(DEC-111·125). iOS `papy.chat.swift` 와 같다.

/** 말풍선 최대 너비(화면 너비 대비가 아니라 고정 — 두 플랫폼이 같은 줄바꿈) */
private val BubbleMaxWidth = 300.dp
private val BubbleCorner = 18.dp
private val BubbleCornerJoined = 6.dp

/**
 * 말풍선. 내 것은 오른쪽·`primary`, 상대 것은 왼쪽·`surface`. 묶음([position])의 이어지는 쪽 모서리는 작게, 마지막은 꼬리 쪽 모서리를 작게.
 * [time] 이 있으면(묶음의 마지막) 오른쪽 아래에 시간·전송 상태를 붙인다. [onLongClick]: 길게 눌러 메뉴.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PapyMessageBubble(
    isMine: Boolean,
    position: BubblePosition,
    modifier: Modifier = Modifier,
    time: String? = null,
    sendState: SendState = SendState.Sent,
    isRead: Boolean = false,
    /** 사진처럼 말풍선 여백 없이 꽉 채우는 내용 */
    fillContent: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val c = PapyTheme.colors
    val joinsPrevious = position == BubblePosition.Middle || position == BubblePosition.Last
    val joinsNext = position == BubblePosition.First || position == BubblePosition.Middle
    val near = if (joinsPrevious) BubbleCornerJoined else BubbleCorner
    val far = if (joinsNext) BubbleCornerJoined else BubbleCornerJoined / 2
    val shape = if (isMine) {
        RoundedCornerShape(topStart = BubbleCorner, topEnd = near, bottomEnd = far, bottomStart = BubbleCorner)
    } else {
        RoundedCornerShape(topStart = near, topEnd = BubbleCorner, bottomEnd = BubbleCorner, bottomStart = far)
    }
    val container = if (isMine) c.primary else c.surface
    val onContainer = if (isMine) c.onPrimary else c.onSurface

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (sendState == SendState.Failed) {
            Icon(
                painter = painterResource(R.drawable.papy_ic_error),
                contentDescription = stringResource(R.string.chat_failed),
                modifier = Modifier.padding(end = PapySpacing.xs).size(PapySize.icon),
                tint = c.error,
            )
        }
        Column(
            modifier = Modifier
                .widthIn(max = BubbleMaxWidth)
                .clip(shape)
                .background(container)
                .then(
                    if (onClick != null || onLongClick != null) {
                        Modifier.combinedClickable(onClick = onClick ?: {}, onLongClick = onLongClick)
                    } else {
                        Modifier
                    },
                )
                .then(if (fillContent) Modifier else Modifier.padding(horizontal = PapySpacing.md, vertical = PapySpacing.sm)),
        ) {
            CompositionLocalProvider(LocalContentColor provides onContainer) {
                content()
                if (time != null) {
                    BubbleMeta(
                        time = time,
                        isMine = isMine,
                        sendState = sendState,
                        isRead = isRead,
                        color = if (fillContent) c.onSurfaceVariant else onContainer.copy(alpha = 0.7f),
                        modifier = Modifier
                            .align(Alignment.End)
                            .then(if (fillContent) Modifier.padding(PapySpacing.xs) else Modifier.padding(top = PapySpacing.xxs)),
                    )
                }
            }
        }
    }
}

/** 말풍선 글자 */
@Composable
fun PapyMessageText(text: String) {
    Text(text = text, style = PapyTheme.typography.body, color = LocalContentColor.current)
}

@Composable
private fun BubbleMeta(time: String, isMine: Boolean, sendState: SendState, isRead: Boolean, color: Color, modifier: Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(PapySpacing.xxs), verticalAlignment = Alignment.CenterVertically) {
        Text(text = time, style = PapyTheme.typography.caption, color = color)
        if (isMine) {
            val icon = when {
                sendState == SendState.Sending -> R.drawable.papy_ic_clock
                sendState == SendState.Failed -> null
                isRead -> R.drawable.papy_ic_check_double
                else -> R.drawable.papy_ic_check
            }
            icon?.let { Icon(painter = painterResource(it), contentDescription = null, modifier = Modifier.size(14.dp), tint = color) }
        }
    }
}

/**
 * 사진 말풍선 내용: 기기 미리보기([preview], 보내는 중) 또는 서버 주소([url]). 둘 다 없으면 사진 아이콘 자리.
 */
@Composable
fun PapyMessageImage(url: String?, preview: Uri?, contentDescription: String?) {
    val model: Any? = preview ?: url?.takeIf { it.isNotBlank() }
    Box(
        modifier = Modifier.widthIn(max = BubbleMaxWidth).fillMaxWidth().aspectRatio(4f / 3f).background(PapyTheme.colors.fill),
        contentAlignment = Alignment.Center,
    ) {
        if (model != null) {
            AsyncImage(model = model, contentDescription = contentDescription, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        } else {
            Icon(painter = painterResource(R.drawable.papy_ic_image), contentDescription = contentDescription, tint = PapyTheme.colors.onSurfaceTertiary)
        }
    }
}

/**
 * 파일 말풍선 내용: 아이콘 + 이름 + [caption](크기·"저장됨" 등). [downloading] 이면 아이콘 자리에 받는 정도([progress], 모르면 돌아가는 표시) — 30-1
 */
@Composable
fun PapyMessageFile(name: String, size: String, downloading: Boolean = false, progress: Float? = null) {
    val color = LocalContentColor.current
    Row(horizontalArrangement = Arrangement.spacedBy(PapySpacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                downloading && progress != null -> CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(24.dp), color = color, strokeWidth = 2.dp)
                downloading -> CircularProgressIndicator(modifier = Modifier.size(24.dp), color = color, strokeWidth = 2.dp)
                else -> Icon(painter = painterResource(R.drawable.papy_ic_file), contentDescription = null, modifier = Modifier.size(PapySize.icon), tint = color)
            }
        }
        Column {
            Text(text = name, style = PapyTheme.typography.bodyStrong, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = size, style = PapyTheme.typography.caption, color = color.copy(alpha = 0.7f))
        }
    }
}

/** 날짜 구분선: 가운데 작은 알약 */
@Composable
fun PapyDateSeparator(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(vertical = PapySpacing.sm), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = PapyTheme.typography.caption,
            color = PapyTheme.colors.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(PapyRadius.full))
                .background(PapyTheme.colors.surface.copy(alpha = 0.9f))
                .padding(horizontal = PapySpacing.md, vertical = PapySpacing.xs),
        )
    }
}

/**
 * 입력창: 첨부 버튼 + 여러 줄 입력(최대 5줄) + 보내기 버튼(글자가 있을 때만 켜짐). 키보드 바로 위에 붙는다(부르는 쪽 imePadding).
 * [onAttach] 가 null 이면 첨부 버튼이 없다(댓글 입력 등), [placeholder] 로 안내 문구를 바꾼다.
 */
@Composable
fun PapyChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: (() -> Unit)?,
    canSend: Boolean,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.chat_input_placeholder),
) {
    val c = PapyTheme.colors
    Surface(modifier = modifier.fillMaxWidth(), color = c.surface) {
        Row(
            modifier = Modifier.navigationBarsPadding().padding(horizontal = PapySpacing.xs, vertical = PapySpacing.xs),
            verticalAlignment = Alignment.Bottom,
        ) {
            if (onAttach != null) {
                IconButton(onClick = onAttach) {
                    Icon(painter = painterResource(R.drawable.papy_ic_attach), contentDescription = stringResource(R.string.attach_title), tint = c.onSurfaceVariant)
                }
            } else {
                Spacer(Modifier.width(PapySpacing.sm))
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = PapySize.controlHeightSmall)
                    .clip(RoundedCornerShape(PapyRadius.lg))
                    .background(c.fill)
                    .padding(horizontal = PapySpacing.md, vertical = PapySpacing.sm),
                textStyle = PapyTheme.typography.body.copy(color = c.onSurface),
                cursorBrush = SolidColor(c.primary),
                maxLines = 5,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(text = placeholder, style = PapyTheme.typography.body, color = c.onSurfaceTertiary)
                        }
                        inner()
                    }
                },
            )
            IconButton(onClick = onSend, enabled = canSend) {
                Icon(
                    painter = painterResource(R.drawable.papy_ic_send),
                    contentDescription = stringResource(R.string.chat_send),
                    tint = if (canSend) c.primary else c.onSurfaceTertiary,
                )
            }
        }
    }
}

/** 맨 아래로 이동 버튼(위로 스크롤했을 때) */
@Composable
fun PapyScrollToBottomButton(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = visible, modifier = modifier, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = PapyTheme.colors.surface,
            shadowElevation = 4.dp,
            modifier = Modifier.size(PapySize.controlHeightSmall),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.papy_ic_chevron_down),
                    contentDescription = stringResource(R.string.chat_scroll_bottom),
                    tint = PapyTheme.colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(name = "라이트")
@Preview(name = "다크", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PapyChatPreview() = PapyTheme {
    Column(
        modifier = Modifier.background(PapyTheme.colors.backgroundGrouped).padding(PapySpacing.lg),
        verticalArrangement = Arrangement.spacedBy(PapySpacing.xxs),
    ) {
        PapyDateSeparator("2026년 10월 26일 월요일")
        PapyMessageBubble(isMine = false, position = BubblePosition.First) { PapyMessageText("안녕하세요!") }
        PapyMessageBubble(isMine = false, position = BubblePosition.Last, time = "오후 3:20") { PapyMessageText("내일 회의 자료 공유드려요.") }
        PapyMessageBubble(isMine = true, position = BubblePosition.First) { PapyMessageText("좋아요") }
        PapyMessageBubble(isMine = true, position = BubblePosition.Middle) { PapyMessageFile("회의록.pdf", "1.2 MB") }
        PapyMessageBubble(isMine = true, position = BubblePosition.Last, time = "오후 3:21", isRead = true) { PapyMessageText("확인했어요 👍") }
        PapyMessageBubble(isMine = true, position = BubblePosition.Single, time = "오후 3:22", sendState = SendState.Failed) { PapyMessageText("보내지 못한 메시지") }
        PapyChatInputBar(value = "", onValueChange = {}, onSend = {}, onAttach = {}, canSend = false)
    }
}
