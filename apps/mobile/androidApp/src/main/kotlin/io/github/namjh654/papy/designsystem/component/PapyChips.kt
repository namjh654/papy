package io.github.namjh654.papy.designsystem.component

import android.content.res.Configuration
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.namjh654.papy.R
import io.github.namjh654.papy.designsystem.theme.PapyTheme
import io.github.namjh654.papy.designsystem.token.PapyRadius
import io.github.namjh654.papy.designsystem.token.PapySize
import io.github.namjh654.papy.designsystem.token.PapySpacing

// 공통 UI 2차(21-1). iOS `papy.chips.swift` 와 같다. 수치는 design/components.md

/**
 * 칩: 둥근 알약. [selected] 면 `primary` 채움, 아니면 `fill`. [onRemove] 가 있으면 오른쪽에 지우기(최근 검색어 등).
 */
@Composable
fun PapyChip(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
) {
    val c = PapyTheme.colors
    val container = if (selected) c.primary else c.fill
    val content = if (selected) c.onPrimary else c.onSurface
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(PapyRadius.full))
            .background(container)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics { this.selected = selected }
            .heightIn(min = 36.dp)
            .padding(start = PapySpacing.md, end = if (onRemove != null) PapySpacing.xs else PapySpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PapySpacing.xxs),
    ) {
        Text(text = label, style = PapyTheme.typography.label, color = content, maxLines = 1)
        if (onRemove != null) {
            Box(modifier = Modifier.size(28.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onRemove), contentAlignment = Alignment.Center) {
                Icon(painter = painterResource(R.drawable.papy_ic_close), contentDescription = stringResource(R.string.search_recent_remove), tint = c.onSurfaceTertiary, modifier = Modifier.size(PapySize.iconSmall))
            }
        }
    }
}

/** 칩 여러 개: 넘치면 다음 줄로 */
@Composable
fun PapyChipGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(PapySpacing.sm),
        verticalArrangement = Arrangement.spacedBy(PapySpacing.sm),
    ) { content() }
}

/**
 * 세그먼트 탭: `fill` 바탕 위에 고른 칸만 `surface` 로 떠 보인다(토스·iOS 세그먼트와 같은 모양). 칸 너비는 같다.
 */
@Composable
fun PapySegmentedTabs(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PapyTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PapyRadius.md))
            .background(c.fill)
            .padding(PapySpacing.xxs),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = PapySize.controlHeightSmall - PapySpacing.xxs * 2)
                    .clip(RoundedCornerShape(PapyRadius.sm))
                    .background(if (selected) c.surface else c.fill)
                    .then(if (selected) Modifier.border(PapySize.border, c.divider, RoundedCornerShape(PapyRadius.sm)) else Modifier)
                    .clickable { onSelect(index) }
                    .semantics {
                        role = Role.Tab
                        this.selected = selected
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = if (selected) PapyTheme.typography.bodyStrong else PapyTheme.typography.body,
                    color = if (selected) c.onSurface else c.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** 스켈레톤: 내용 자리에 `fill` 색 상자가 천천히 깜박인다(1초 주기) */
@Composable
fun PapySkeleton(modifier: Modifier = Modifier, radius: Dp = PapyRadius.sm) {
    val alpha by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 1f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1_000), RepeatMode.Reverse),
        label = "alpha",
    )
    Box(modifier = modifier.clip(RoundedCornerShape(radius)).alpha(alpha).background(PapyTheme.colors.fill))
}

/** 목록 행 자리(아바타 + 두 줄) — 목록 첫 로딩 */
@Composable
fun PapySkeletonRow(modifier: Modifier = Modifier, avatar: Dp = 48.dp) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = PapySpacing.xl, vertical = PapySpacing.md),
        horizontalArrangement = Arrangement.spacedBy(PapySpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PapySkeleton(modifier = Modifier.size(avatar), radius = PapyRadius.full)
        Column(verticalArrangement = Arrangement.spacedBy(PapySpacing.sm), modifier = Modifier.weight(1f)) {
            PapySkeleton(modifier = Modifier.fillMaxWidth(0.4f).height(14.dp))
            PapySkeleton(modifier = Modifier.fillMaxWidth(0.8f).height(12.dp))
        }
    }
}

/** 글 카드 자리 — 커뮤니티 첫 로딩 */
@Composable
fun PapySkeletonCard(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PapyRadius.xl))
            .background(PapyTheme.colors.surface)
            .padding(PapySpacing.lg),
        verticalArrangement = Arrangement.spacedBy(PapySpacing.sm),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(PapySpacing.sm), verticalAlignment = Alignment.CenterVertically) {
            PapySkeleton(modifier = Modifier.size(40.dp), radius = PapyRadius.full)
            PapySkeleton(modifier = Modifier.fillMaxWidth(0.3f).height(14.dp))
        }
        PapySkeleton(modifier = Modifier.fillMaxWidth().height(12.dp))
        PapySkeleton(modifier = Modifier.fillMaxWidth(0.7f).height(12.dp))
    }
}

/**
 * 검색창: 둥근 채움 칸, 앞 돋보기, 글자가 있으면 뒤에 지우기, 키보드 검색 키 → [onSearch].
 */
@Composable
fun PapySearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.search_placeholder),
) {
    val c = PapyTheme.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        textStyle = PapyTheme.typography.body.copy(color = c.onSurface),
        cursorBrush = SolidColor(c.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        decorationBox = { inner ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(PapyRadius.full))
                    .background(c.fill)
                    .heightIn(min = 44.dp)
                    .padding(start = PapySpacing.md, end = PapySpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PapySpacing.sm),
            ) {
                Icon(painter = painterResource(R.drawable.papy_ic_search), contentDescription = null, tint = c.onSurfaceTertiary, modifier = Modifier.size(PapySize.icon))
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(text = placeholder, style = PapyTheme.typography.body, color = c.onSurfaceTertiary, maxLines = 1)
                    inner()
                }
                if (value.isNotEmpty()) {
                    Box(modifier = Modifier.size(36.dp).clip(CircleShape).clickable { onValueChange("") }, contentAlignment = Alignment.Center) {
                        Icon(painter = painterResource(R.drawable.papy_ic_close), contentDescription = stringResource(R.string.search_clear), tint = c.onSurfaceTertiary, modifier = Modifier.size(PapySize.iconSmall))
                    }
                }
            }
        },
    )
}

@Preview(name = "라이트")
@Preview(name = "다크", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PapyChipsPreview() = PapyTheme {
    Column(modifier = Modifier.background(PapyTheme.colors.background).padding(PapySpacing.lg), verticalArrangement = Arrangement.spacedBy(PapySpacing.lg)) {
        PapySearchBar(value = "엘사", onValueChange = {}, onSearch = {})
        PapyChipGroup {
            PapyChip("전체", selected = true, onClick = {})
            PapyChip("인기", onClick = {})
            PapyChip("최근 검색어", onClick = {}, onRemove = {})
        }
        PapySegmentedTabs(options = listOf("전체", "내 글"), selectedIndex = 0, onSelect = {})
        PapySkeletonRow()
        PapySkeletonCard()
    }
}
