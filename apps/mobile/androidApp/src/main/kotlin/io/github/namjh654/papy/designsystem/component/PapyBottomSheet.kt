package io.github.namjh654.papy.designsystem.component

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.namjh654.papy.R
import io.github.namjh654.papy.designsystem.theme.PapyTheme
import io.github.namjh654.papy.designsystem.token.PapySize
import io.github.namjh654.papy.designsystem.token.PapySpacing
import kotlinx.coroutines.launch

/**
 * 공통 바텀시트: 내용 높이만큼 열리고(반쯤 열린 단계 없음), 끌어내리거나 바깥을 누르면 닫힌다. iOS `.papyBottomSheet` 와 같다.
 * 보일지 말지는 부르는 쪽 상태로 정한다(`if (show) PapyBottomSheet(onDismiss = { show = false }) { … }`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PapyBottomSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = PapyTheme.colors.surface,
        contentColor = PapyTheme.colors.onSurface,
    ) {
        Column(modifier = Modifier.navigationBarsPadding().padding(bottom = PapySpacing.lg)) {
            title?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(horizontal = PapySpacing.xl, vertical = PapySpacing.md),
                    style = PapyTheme.typography.title,
                )
            }
            content()
        }
    }
}

/**
 * 액션 메뉴의 한 항목. [destructive] 는 삭제처럼 되돌릴 수 없는 동작(빨간 글자).
 * iOS `PapyAction` 과 같다.
 */
data class PapyAction(
    val label: String,
    @DrawableRes val icon: Int? = null,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * 액션 메뉴(길게 눌러 메뉴·첨부 메뉴 등): 바텀시트 위의 선택지. 누르면 시트가 닫힌 뒤 그 항목의 동작을 한다.
 * iOS `.papyActionSheet` 와 같다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PapyActionSheet(
    actions: List<PapyAction>,
    onDismiss: () -> Unit,
    title: String? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PapyTheme.colors.surface,
        contentColor = PapyTheme.colors.onSurface,
    ) {
        PapyActionList(
            actions = actions,
            title = title,
            // 시트가 다 내려간 뒤 동작한다 — 카메라·파일 선택처럼 새 화면을 여는 동작이 시트와 겹치지 않게
            onClick = { action ->
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    onDismiss()
                    action.onClick()
                }
            },
        )
    }
}

/** 액션 메뉴 본문(미리보기 대상, DEC-122) */
@Composable
private fun PapyActionList(
    actions: List<PapyAction>,
    title: String?,
    onClick: (PapyAction) -> Unit,
) {
    Column(modifier = Modifier.navigationBarsPadding().padding(bottom = PapySpacing.lg)) {
        title?.let {
            Text(
                text = it,
                modifier = Modifier.padding(horizontal = PapySpacing.xl, vertical = PapySpacing.md),
                style = PapyTheme.typography.bodySmall,
                color = PapyTheme.colors.onSurfaceVariant,
            )
        }
        actions.forEach { action ->
            val color = if (action.destructive) PapyTheme.colors.error else PapyTheme.colors.onSurface
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = PapySize.controlHeight)
                    .clickable { onClick(action) }
                    .padding(horizontal = PapySpacing.xl),
                horizontalArrangement = Arrangement.spacedBy(PapySpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                action.icon?.let {
                    Icon(painter = painterResource(it), contentDescription = null, modifier = Modifier.size(PapySize.icon), tint = color)
                }
                Text(text = action.label, style = PapyTheme.typography.body, color = color)
            }
        }
    }
}

@Preview(name = "라이트")
@Preview(name = "다크", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PapyActionListPreview() = PapyTheme {
    Column(modifier = Modifier.background(PapyTheme.colors.surface)) {
        PapyActionList(
            title = "첨부",
            actions = listOf(
                PapyAction("사진", R.drawable.papy_ic_image) {},
                PapyAction("카메라", R.drawable.papy_ic_camera) {},
                PapyAction("파일", R.drawable.papy_ic_file) {},
                PapyAction("삭제", R.drawable.papy_ic_delete, destructive = true) {},
            ),
            onClick = {},
        )
    }
}
