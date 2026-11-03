package cloud.elsa.app.designsystem.component

import androidx.compose.ui.semantics.Role
import android.content.res.Configuration
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cloud.elsa.app.R
import cloud.elsa.app.designsystem.theme.ElsaTheme
import cloud.elsa.app.designsystem.token.ElsaRadius
import cloud.elsa.app.designsystem.token.ElsaSize
import cloud.elsa.app.designsystem.token.ElsaSpacing
import cloud.elsa.app.feature.chat.room.BubblePosition
import coil3.compose.AsyncImage

// 채팅 고도화(22-2). iOS `elsa.chat.extras.swift` 와 같다

/**
 * 말풍선 안 인용 블록: 왼쪽 막대 3 + 이름(label)·원문 한 줄(bodySmall). 색은 말풍선 글자색을 따른다. 누르면 원문으로.
 */
@Composable
fun ElsaReplyQuote(name: String, text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(ElsaRadius.xs))
            .background(color.copy(alpha = 0.12f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(end = ElsaSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(ElsaSpacing.sm),
    ) {
        Box(Modifier.width(3.dp).height(40.dp).background(color.copy(alpha = 0.8f)))
        Column(modifier = Modifier.padding(vertical = ElsaSpacing.xs)) {
            Text(text = name, style = ElsaTheme.typography.label, color = color, maxLines = 1)
            Text(text = text, style = ElsaTheme.typography.bodySmall, color = color.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 입력창 위 답장 막대: 왼쪽 `primary` 막대 + "답장 · 이름" + 원문 한 줄 + 취소 */
@Composable
fun ElsaReplyBar(name: String, text: String, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val c = ElsaTheme.colors
    Row(
        modifier = modifier.fillMaxWidth().background(c.surface).padding(start = ElsaSpacing.md, top = ElsaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ElsaSpacing.sm),
    ) {
        Icon(painter = painterResource(R.drawable.elsa_ic_reply), contentDescription = null, tint = c.primary, modifier = Modifier.size(ElsaSize.icon))
        Box(Modifier.width(3.dp).height(36.dp).background(c.primary))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "${stringResource(R.string.chat_reply)} · $name", style = ElsaTheme.typography.label, color = c.primary, maxLines = 1)
            Text(text = text, style = ElsaTheme.typography.bodySmall, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onCancel) {
            Icon(painter = painterResource(R.drawable.elsa_ic_close), contentDescription = stringResource(R.string.chat_cancel_reply), tint = c.onSurfaceTertiary)
        }
    }
}

/** 상대가 입력 중: 상대 말풍선 모양 안에 점 3개가 차례로 깜박인다 */
@Composable
fun ElsaTypingIndicator(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "typing")
    val typingLabel = stringResource(R.string.chat_typing)
    ElsaMessageBubble(isMine = false, position = BubblePosition.Single, modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(ElsaSpacing.xs),
            modifier = Modifier.padding(vertical = ElsaSpacing.xs).semantics { contentDescription = typingLabel },
        ) {
            repeat(3) { i ->
                val alpha by transition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(durationMillis = 600, delayMillis = i * 200), RepeatMode.Reverse),
                    label = "dot$i",
                )
                Box(Modifier.size(8.dp).alpha(alpha).clip(CircleShape).background(ElsaTheme.colors.onSurfaceVariant))
            }
        }
    }
}

/**
 * 사진 전체 화면: 검은 바탕, 두 손가락으로 확대(1~4배)·이동, 두 번 누르면 원래 크기, 위 닫기.
 * [model] 은 기기 Uri 또는 주소.
 */
@Composable
fun ElsaImageViewer(model: Any?, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        val transform = rememberTransformableState { zoom, pan, _ ->
            scale = (scale * zoom).coerceIn(1f, 4f)
            offset = if (scale == 1f) Offset.Zero else offset + pan
        }
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = model,
                contentDescription = stringResource(R.string.chat_attachment_photo),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .transformable(transform)
                    .clickable(interactionSource = null, indication = null) {
                        scale = 1f
                        offset = Offset.Zero
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
            IconButton(onClick = onDismiss, modifier = Modifier.statusBarsPadding().padding(ElsaSpacing.sm).align(Alignment.TopEnd)) {
                Icon(painter = painterResource(R.drawable.elsa_ic_close), contentDescription = stringResource(R.string.chat_close), tint = Color.White)
            }
        }
    }
}

@Preview(name = "라이트")
@Preview(name = "다크", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ElsaChatExtrasPreview() = ElsaTheme {
    Column(modifier = Modifier.background(ElsaTheme.colors.backgroundGrouped).padding(ElsaSpacing.lg), verticalArrangement = Arrangement.spacedBy(ElsaSpacing.sm)) {
        ElsaMessageBubble(isMine = true, position = BubblePosition.Single, time = "오후 3:21") {
            Column(verticalArrangement = Arrangement.spacedBy(ElsaSpacing.xs)) {
                ElsaReplyQuote(name = "엘사", text = "내일 회의 자료 공유드려요.", onClick = {})
                ElsaMessageText("확인했어요 👍")
            }
        }
        ElsaTypingIndicator()
        ElsaReplyBar(name = "엘사", text = "내일 회의 자료 공유드려요.", onCancel = {})
    }
}
