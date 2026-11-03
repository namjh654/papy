package io.github.namjh654.papy.designsystem.component

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
import io.github.namjh654.papy.R
import io.github.namjh654.papy.designsystem.theme.PapyTheme
import io.github.namjh654.papy.designsystem.token.PapyRadius
import io.github.namjh654.papy.designsystem.token.PapySize
import io.github.namjh654.papy.designsystem.token.PapySpacing

// 커뮤니티 UI(20-2). iOS `papy.post.swift` 와 같다. 수치는 design/components.md "커뮤니티"

/** 글쓴이 줄: 아바타 40 + 이름(bodyStrong)·시간(caption) + 더보기. [onAuthorClick] 이 있으면 아바타·이름을 눌러 프로필로(25-1) */
@Composable
fun PapyAuthorRow(
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
                .clip(RoundedCornerShape(PapyRadius.md))
                .then(if (onAuthorClick != null) Modifier.clickable(role = Role.Button, onClick = onAuthorClick) else Modifier),
            horizontalArrangement = Arrangement.spacedBy(PapySpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PapyAvatar(name = name, url = avatarUrl, size = 40.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = name, style = PapyTheme.typography.bodyStrong, color = PapyTheme.colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = time, style = PapyTheme.typography.caption, color = PapyTheme.colors.onSurfaceTertiary)
            }
        }
        if (onMore != null) {
            IconButton(onClick = onMore) {
                Icon(painter = painterResource(R.drawable.papy_ic_more), contentDescription = stringResource(R.string.community_more), tint = PapyTheme.colors.onSurfaceVariant)
            }
        }
    }
}

/**
 * 사진 묶음 1~4장: 1장 16:9, 2장 나란히 정사각, 3장 왼쪽 큰 것 + 오른쪽 둘, 4장 2×2. 사이 간격 `spacing.xxs`, 모서리 `radius.md`.
 * [image] 가 i 번째 사진을 그린다(원격 주소·기기 미리보기는 부르는 쪽이 정한다).
 */
@Composable
fun PapyImageGrid(
    count: Int,
    modifier: Modifier = Modifier,
    image: @Composable (index: Int, modifier: Modifier) -> Unit,
) {
    if (count <= 0) return
    val gap = PapySpacing.xxs
    val cell = Modifier.fillMaxSize()
    Box(modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(PapyRadius.md))) {
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
fun PapyPostImage(url: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(PapyTheme.colors.fill), contentAlignment = Alignment.Center) {
        if (url.isNotBlank()) {
            PapyRemoteImage(url = url, contentDescription = stringResource(R.string.community_photo), modifier = Modifier.fillMaxSize())
        } else {
            Icon(painter = painterResource(R.drawable.papy_ic_image), contentDescription = stringResource(R.string.community_photo), tint = PapyTheme.colors.onSurfaceTertiary)
        }
    }
}

/** 좋아요·댓글 수 버튼. 좋아요를 누른 상태는 채운 하트 + `error` 색 */
@Composable
fun PapyReactionButton(
    icon: Int,
    count: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    val color = if (active) PapyTheme.colors.error else PapyTheme.colors.onSurfaceVariant
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(PapyRadius.full))
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = PapySize.controlHeightSmall)
            .padding(horizontal = PapySpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(PapySpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painter = painterResource(icon), contentDescription = contentDescription, modifier = Modifier.size(PapySize.icon), tint = color)
        Text(text = "$count", style = PapyTheme.typography.label, color = color)
    }
}

/** 좋아요 버튼 */
@Composable
fun PapyLikeButton(liked: Boolean, count: Int, onClick: () -> Unit) {
    PapyReactionButton(
        icon = if (liked) R.drawable.papy_ic_heart_filled else R.drawable.papy_ic_heart,
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
fun PapyPostCard(
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
            .clip(RoundedCornerShape(PapyRadius.xl))
            .background(PapyTheme.colors.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = PapySpacing.lg, end = PapySpacing.xs, top = PapySpacing.md, bottom = PapySpacing.xs),
        verticalArrangement = Arrangement.spacedBy(PapySpacing.sm),
    ) {
        PapyAuthorRow(name = authorName, avatarUrl = avatarUrl, time = time, onMore = onMore, onAuthorClick = onAuthorClick)
        Text(
            text = content,
            style = PapyTheme.typography.body,
            color = PapyTheme.colors.onSurface,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = PapySpacing.md),
        )
        PapyImageGrid(count = imageCount, modifier = Modifier.padding(end = PapySpacing.md), image = image)
        Row(horizontalArrangement = Arrangement.spacedBy(PapySpacing.xs), modifier = Modifier.offset(x = -PapySpacing.sm)) {
            PapyLikeButton(liked = liked, count = likeCount, onClick = onLike)
            PapyReactionButton(icon = R.drawable.papy_ic_comment, count = commentCount, contentDescription = stringResource(R.string.community_comments), onClick = onClick)
        }
    }
}

/** 댓글 한 줄: 아바타 32 + 이름·시간 + 내용, 길게 누르거나 더보기로 메뉴. [onAuthorClick]: 아바타를 눌러 프로필로 */
@Composable
fun PapyCommentRow(
    authorName: String,
    avatarUrl: String?,
    time: String,
    content: String,
    onMore: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onAuthorClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = PapySpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(PapySpacing.sm),
    ) {
        Box(modifier = Modifier.clip(CircleShape).then(if (onAuthorClick != null) Modifier.clickable(role = Role.Button, onClick = onAuthorClick) else Modifier)) {
            PapyAvatar(name = authorName, url = avatarUrl, size = 32.dp)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PapySpacing.xxs)) {
            Row(horizontalArrangement = Arrangement.spacedBy(PapySpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Text(text = authorName, style = PapyTheme.typography.label, color = PapyTheme.colors.onSurface)
                Text(text = time, style = PapyTheme.typography.caption, color = PapyTheme.colors.onSurfaceTertiary)
            }
            Text(text = content, style = PapyTheme.typography.body, color = PapyTheme.colors.onSurface)
        }
        if (onMore != null) {
            IconButton(onClick = onMore, modifier = Modifier.size(PapySize.controlHeightSmall)) {
                Icon(painter = painterResource(R.drawable.papy_ic_more), contentDescription = stringResource(R.string.community_more), tint = PapyTheme.colors.onSurfaceTertiary)
            }
        }
    }
}

/** 화면 오른쪽 아래에 뜨는 둥근 동작 버튼(글쓰기 등). 지름 `controlHeight`, `primary`. iOS `PapyFloatingButton` 과 같다 */
@Composable
fun PapyFloatingButton(icon: Int, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = PapyTheme.colors.primary,
        contentColor = PapyTheme.colors.onPrimary,
        shadowElevation = 6.dp,
        modifier = modifier.size(PapySize.controlHeight),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painter = painterResource(icon), contentDescription = contentDescription, modifier = Modifier.size(PapySize.iconLarge))
        }
    }
}

@Preview(name = "라이트")
@Preview(name = "다크", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PapyPostPreview() = PapyTheme {
    Column(
        modifier = Modifier.background(PapyTheme.colors.backgroundGrouped).padding(PapySpacing.lg),
        verticalArrangement = Arrangement.spacedBy(PapySpacing.md),
    ) {
        PapyPostCard(
            authorName = "엘사", avatarUrl = null, time = "5분 전",
            content = "오늘 날씨가 너무 좋아요. 산책 다녀왔어요! 다들 주말 잘 보내세요.",
            imageCount = 3, liked = true, likeCount = 12, commentCount = 3,
            onClick = {}, onLike = {}, onMore = {},
            image = { _, m -> PapyPostImage(url = "", modifier = m) },
        )
        PapyCommentRow(authorName = "안나", avatarUrl = null, time = "방금 전", content = "좋아요!", onMore = {})
    }
}
