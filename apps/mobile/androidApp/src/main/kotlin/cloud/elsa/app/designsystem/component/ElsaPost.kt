package cloud.elsa.app.designsystem.component

import androidx.compose.ui.semantics.Role
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cloud.elsa.app.R
import cloud.elsa.app.designsystem.theme.ElsaTheme
import cloud.elsa.app.designsystem.token.ElsaRadius
import cloud.elsa.app.designsystem.token.ElsaSize
import cloud.elsa.app.designsystem.token.ElsaSpacing

// 커뮤니티 UI(20-2). iOS `elsa.post.swift` 와 같다. 수치는 design/components.md "커뮤니티"

/** 글쓴이 줄: 아바타 40 + 이름(bodyStrong)·시간(caption) + 더보기. [onAuthorClick] 이 있으면 아바타·이름을 눌러 프로필로(25-1) */
@Composable
fun ElsaAuthorRow(
    name: String,
    avatarUrl: String?,
    time: String,
    onMore: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onAuthorClick: (() -> Unit)? = null,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(ElsaRadius.md))
                .then(if (onAuthorClick != null) Modifier.clickable(role = Role.Button, onClick = onAuthorClick) else Modifier),
            horizontalArrangement = Arrangement.spacedBy(ElsaSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ElsaAvatar(name = name, url = avatarUrl, size = 40.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = name, style = ElsaTheme.typography.bodyStrong, color = ElsaTheme.colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = time, style = ElsaTheme.typography.caption, color = ElsaTheme.colors.onSurfaceTertiary)
            }
        }
        if (onMore != null) {
            IconButton(onClick = onMore) {
                Icon(painter = painterResource(R.drawable.elsa_ic_more), contentDescription = stringResource(R.string.community_more), tint = ElsaTheme.colors.onSurfaceVariant)
            }
        }
    }
}

/**
 * 사진 묶음 1~4장: 1장 16:9, 2장 나란히 정사각, 3장 왼쪽 큰 것 + 오른쪽 둘, 4장 2×2. 사이 간격 `spacing.xxs`, 모서리 `radius.md`.
 * [image] 가 i 번째 사진을 그린다(원격 주소·기기 미리보기는 부르는 쪽이 정한다).
 */
@Composable
fun ElsaImageGrid(
    count: Int,
    modifier: Modifier = Modifier,
    image: @Composable (index: Int, modifier: Modifier) -> Unit,
) {
    if (count <= 0) return
    val gap = ElsaSpacing.xxs
    val cell = Modifier.fillMaxSize()
    Box(modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(ElsaRadius.md))) {
        when (count) {
            1 -> Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) { image(0, cell) }
            2 -> Row(Modifier.fillMaxWidth().aspectRatio(2f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                repeat(2) { i -> Box(Modifier.weight(1f).fillMaxHeight()) { image(i, cell) } }
            }
            3 -> Row(Modifier.fillMaxWidth().aspectRatio(3f / 2f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                Box(Modifier.weight(2f).fillMaxHeight()) { image(0, cell) }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    repeat(2) { i -> Box(Modifier.weight(1f).fillMaxWidth()) { image(i + 1, cell) } }
                }
            }
            else -> Column(Modifier.fillMaxWidth().aspectRatio(1f), verticalArrangement = Arrangement.spacedBy(gap)) {
                repeat(2) { row ->
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        repeat(2) { col -> Box(Modifier.weight(1f).fillMaxHeight()) { image(row * 2 + col, cell) } }
                    }
                }
            }
        }
    }
}

/** 원격 사진 한 칸(주소가 비어 있으면 회색 자리) */
@Composable
fun ElsaPostImage(url: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(ElsaTheme.colors.fill), contentAlignment = Alignment.Center) {
        if (url.isNotBlank()) {
            ElsaRemoteImage(url = url, contentDescription = stringResource(R.string.community_photo), modifier = Modifier.fillMaxSize())
        } else {
            Icon(painter = painterResource(R.drawable.elsa_ic_image), contentDescription = stringResource(R.string.community_photo), tint = ElsaTheme.colors.onSurfaceTertiary)
        }
    }
}

/** 좋아요·댓글 수 버튼. 좋아요를 누른 상태는 채운 하트 + `error` 색 */
@Composable
fun ElsaReactionButton(
    icon: Int,
    count: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    val color = if (active) ElsaTheme.colors.error else ElsaTheme.colors.onSurfaceVariant
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(ElsaRadius.full))
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = ElsaSize.controlHeightSmall)
            .padding(horizontal = ElsaSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(ElsaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painter = painterResource(icon), contentDescription = contentDescription, modifier = Modifier.size(ElsaSize.icon), tint = color)
        Text(text = "$count", style = ElsaTheme.typography.label, color = color)
    }
}

/** 좋아요 버튼 */
@Composable
fun ElsaLikeButton(liked: Boolean, count: Int, onClick: () -> Unit) {
    ElsaReactionButton(
        icon = if (liked) R.drawable.elsa_ic_heart_filled else R.drawable.elsa_ic_heart,
        count = count,
        contentDescription = stringResource(R.string.community_like),
        onClick = onClick,
        active = liked,
    )
}

/**
 * 글 카드(목록): 무테 카드 위에 글쓴이 줄 → 본문(최대 [maxLines] 줄) → 사진 묶음 → 좋아요·댓글.
 * 목록은 `backgroundGrouped` 위, 카드는 `surface`.
 */
@Composable
fun ElsaPostCard(
    authorName: String,
    avatarUrl: String?,
    time: String,
    content: String,
    imageCount: Int,
    liked: Boolean,
    likeCount: Int,
    commentCount: Int,
    onClick: () -> Unit,
    onLike: () -> Unit,
    onMore: (() -> Unit)?,
    modifier: Modifier = Modifier,
    maxLines: Int = 3,
    onAuthorClick: (() -> Unit)? = null,
    image: @Composable (index: Int, modifier: Modifier) -> Unit = { _, _ -> },
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ElsaRadius.xl))
            .background(ElsaTheme.colors.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = ElsaSpacing.lg, end = ElsaSpacing.xs, top = ElsaSpacing.md, bottom = ElsaSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(ElsaSpacing.sm),
    ) {
        ElsaAuthorRow(name = authorName, avatarUrl = avatarUrl, time = time, onMore = onMore, onAuthorClick = onAuthorClick)
        Text(
            text = content,
            style = ElsaTheme.typography.body,
            color = ElsaTheme.colors.onSurface,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = ElsaSpacing.md),
        )
        ElsaImageGrid(count = imageCount, modifier = Modifier.padding(end = ElsaSpacing.md), image = image)
        Row(horizontalArrangement = Arrangement.spacedBy(ElsaSpacing.xs), modifier = Modifier.offset(x = -ElsaSpacing.sm)) {
            ElsaLikeButton(liked = liked, count = likeCount, onClick = onLike)
            ElsaReactionButton(icon = R.drawable.elsa_ic_comment, count = commentCount, contentDescription = stringResource(R.string.community_comments), onClick = onClick)
        }
    }
}

/** 댓글 한 줄: 아바타 32 + 이름·시간 + 내용, 길게 누르거나 더보기로 메뉴. [onAuthorClick]: 아바타를 눌러 프로필로 */
@Composable
fun ElsaCommentRow(
    authorName: String,
    avatarUrl: String?,
    time: String,
    content: String,
    onMore: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onAuthorClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = ElsaSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(ElsaSpacing.sm),
    ) {
        Box(modifier = Modifier.clip(CircleShape).then(if (onAuthorClick != null) Modifier.clickable(role = Role.Button, onClick = onAuthorClick) else Modifier)) {
            ElsaAvatar(name = authorName, url = avatarUrl, size = 32.dp)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ElsaSpacing.xxs)) {
            Row(horizontalArrangement = Arrangement.spacedBy(ElsaSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Text(text = authorName, style = ElsaTheme.typography.label, color = ElsaTheme.colors.onSurface)
                Text(text = time, style = ElsaTheme.typography.caption, color = ElsaTheme.colors.onSurfaceTertiary)
            }
            Text(text = content, style = ElsaTheme.typography.body, color = ElsaTheme.colors.onSurface)
        }
        if (onMore != null) {
            IconButton(onClick = onMore, modifier = Modifier.size(ElsaSize.controlHeightSmall)) {
                Icon(painter = painterResource(R.drawable.elsa_ic_more), contentDescription = stringResource(R.string.community_more), tint = ElsaTheme.colors.onSurfaceTertiary)
            }
        }
    }
}

/** 화면 오른쪽 아래에 뜨는 둥근 동작 버튼(글쓰기 등). 지름 `controlHeight`, `primary`. iOS `ElsaFloatingButton` 과 같다 */
@Composable
fun ElsaFloatingButton(icon: Int, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = ElsaTheme.colors.primary,
        contentColor = ElsaTheme.colors.onPrimary,
        shadowElevation = 6.dp,
        modifier = modifier.size(ElsaSize.controlHeight),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painter = painterResource(icon), contentDescription = contentDescription, modifier = Modifier.size(ElsaSize.iconLarge))
        }
    }
}

@Preview(name = "라이트")
@Preview(name = "다크", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ElsaPostPreview() = ElsaTheme {
    Column(
        modifier = Modifier.background(ElsaTheme.colors.backgroundGrouped).padding(ElsaSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(ElsaSpacing.md),
    ) {
        ElsaPostCard(
            authorName = "엘사", avatarUrl = null, time = "5분 전",
            content = "오늘 날씨가 너무 좋아요. 산책 다녀왔어요! 다들 주말 잘 보내세요.",
            imageCount = 3, liked = true, likeCount = 12, commentCount = 3,
            onClick = {}, onLike = {}, onMore = {},
            image = { _, m -> ElsaPostImage(url = "", modifier = m) },
        )
        ElsaCommentRow(authorName = "안나", avatarUrl = null, time = "방금 전", content = "좋아요!", onMore = {})
    }
}
