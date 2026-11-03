package cloud.elsa.app.designsystem.component

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
import cloud.elsa.app.R
import cloud.elsa.app.designsystem.theme.ElsaTheme
import cloud.elsa.app.designsystem.token.ElsaRadius
import cloud.elsa.app.designsystem.token.ElsaSize
import cloud.elsa.app.designsystem.token.ElsaSpacing

// 공통 UI 2차(21-1). iOS `elsa.chips.swift` 와 같다. 수치는 design/components.md

/**
 * 칩: 둥근 알약. [selected] 면 `primary` 채움, 아니면 `fill`. [onRemove] 가 있으면 오른쪽에 지우기(최근 검색어 등).
 */
@Composable
fun ElsaChip(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
) {
    val c = ElsaTheme.colors
    val container = if (selected) c.primary else c.fill
    val content = if (selected) c.onPrimary else c.onSurface
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(ElsaRadius.full))
            .background(container)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics { this.selected = selected }
            .heightIn(min = 36.dp)
            .padding(start = ElsaSpacing.md, end = if (onRemove != null) ElsaSpacing.xs else ElsaSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ElsaSpacing.xxs),
    ) {
        Text(text = label, style = ElsaTheme.typography.label, color = content, maxLines = 1)
        if (onRemove != null) {
            Box(modifier = Modifier.size(28.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onRemove), contentAlignment = Alignment.Center) {
                Icon(painter = painterResource(R.drawable.elsa_ic_close), contentDescription = stringResource(R.string.search_recent_remove), tint = c.onSurfaceTertiary, modifier = Modifier.size(ElsaSize.iconSmall))
            }
        }
    }
}

/** 칩 여러 개: 넘치면 다음 줄로 */
@Composable
fun ElsaChipGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ElsaSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(ElsaSpacing.sm),
    ) { content() }
}

/**
 * 세그먼트 탭: `fill` 바탕 위에 고른 칸만 `surface` 로 떠 보인다(토스·iOS 세그먼트와 같은 모양). 칸 너비는 같다.
 */
@Composable
fun ElsaSegmentedTabs(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = ElsaTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ElsaRadius.md))
            .background(c.fill)
            .padding(ElsaSpacing.xxs),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = ElsaSize.controlHeightSmall - ElsaSpacing.xxs * 2)
                    .clip(RoundedCornerShape(ElsaRadius.sm))
                    .background(if (selected) c.surface else c.fill)
                    .then(if (selected) Modifier.border(ElsaSize.border, c.divider, RoundedCornerShape(ElsaRadius.sm)) else Modifier)
                    .clickable { onSelect(index) }
                    .semantics {
                        role = Role.Tab
                        this.selected = selected
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = if (selected) ElsaTheme.typography.bodyStrong else ElsaTheme.typography.body,
                    color = if (selected) c.onSurface else c.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** 스켈레톤: 내용 자리에 `fill` 색 상자가 천천히 깜박인다(1초 주기) */
@Composable
fun ElsaSkeleton(modifier: Modifier = Modifier, radius: Dp = ElsaRadius.sm) {
    val alpha by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 1f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1_000), RepeatMode.Reverse),
        label = "alpha",
    )
    Box(modifier = modifier.clip(RoundedCornerShape(radius)).alpha(alpha).background(ElsaTheme.colors.fill))
}

/** 목록 행 자리(아바타 + 두 줄) — 목록 첫 로딩 */
@Composable
fun ElsaSkeletonRow(modifier: Modifier = Modifier, avatar: Dp = 48.dp) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = ElsaSpacing.xl, vertical = ElsaSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(ElsaSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ElsaSkeleton(modifier = Modifier.size(avatar), radius = ElsaRadius.full)
        Column(verticalArrangement = Arrangement.spacedBy(ElsaSpacing.sm), modifier = Modifier.weight(1f)) {
            ElsaSkeleton(modifier = Modifier.fillMaxWidth(0.4f).height(14.dp))
            ElsaSkeleton(modifier = Modifier.fillMaxWidth(0.8f).height(12.dp))
        }
    }
}

/** 글 카드 자리 — 커뮤니티 첫 로딩 */
@Composable
fun ElsaSkeletonCard(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ElsaRadius.xl))
            .background(ElsaTheme.colors.surface)
            .padding(ElsaSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(ElsaSpacing.sm),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(ElsaSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
            ElsaSkeleton(modifier = Modifier.size(40.dp), radius = ElsaRadius.full)
            ElsaSkeleton(modifier = Modifier.fillMaxWidth(0.3f).height(14.dp))
        }
        ElsaSkeleton(modifier = Modifier.fillMaxWidth().height(12.dp))
        ElsaSkeleton(modifier = Modifier.fillMaxWidth(0.7f).height(12.dp))
    }
}

/**
 * 검색창: 둥근 채움 칸, 앞 돋보기, 글자가 있으면 뒤에 지우기, 키보드 검색 키 → [onSearch].
 */
@Composable
fun ElsaSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.search_placeholder),
) {
    val c = ElsaTheme.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        textStyle = ElsaTheme.typography.body.copy(color = c.onSurface),
        cursorBrush = SolidColor(c.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        decorationBox = { inner ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(ElsaRadius.full))
                    .background(c.fill)
                    .heightIn(min = 44.dp)
                    .padding(start = ElsaSpacing.md, end = ElsaSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ElsaSpacing.sm),
            ) {
                Icon(painter = painterResource(R.drawable.elsa_ic_search), contentDescription = null, tint = c.onSurfaceTertiary, modifier = Modifier.size(ElsaSize.icon))
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(text = placeholder, style = ElsaTheme.typography.body, color = c.onSurfaceTertiary, maxLines = 1)
                    inner()
                }
                if (value.isNotEmpty()) {
                    Box(modifier = Modifier.size(36.dp).clip(CircleShape).clickable { onValueChange("") }, contentAlignment = Alignment.Center) {
                        Icon(painter = painterResource(R.drawable.elsa_ic_close), contentDescription = stringResource(R.string.search_clear), tint = c.onSurfaceTertiary, modifier = Modifier.size(ElsaSize.iconSmall))
                    }
                }
            }
        },
    )
}

@Preview(name = "라이트")
@Preview(name = "다크", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ElsaChipsPreview() = ElsaTheme {
    Column(modifier = Modifier.background(ElsaTheme.colors.background).padding(ElsaSpacing.lg), verticalArrangement = Arrangement.spacedBy(ElsaSpacing.lg)) {
        ElsaSearchBar(value = "엘사", onValueChange = {}, onSearch = {})
        ElsaChipGroup {
            ElsaChip("전체", selected = true, onClick = {})
            ElsaChip("인기", onClick = {})
            ElsaChip("최근 검색어", onClick = {}, onRemove = {})
        }
        ElsaSegmentedTabs(options = listOf("전체", "내 글"), selectedIndex = 0, onSelect = {})
        ElsaSkeletonRow()
        ElsaSkeletonCard()
    }
}
