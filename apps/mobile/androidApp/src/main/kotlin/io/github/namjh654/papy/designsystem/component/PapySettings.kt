package io.github.namjh654.papy.designsystem.component

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.namjh654.papy.R
import io.github.namjh654.papy.designsystem.theme.PapyTheme
import io.github.namjh654.papy.designsystem.token.PapySize
import io.github.namjh654.papy.designsystem.token.PapySpacing

/** 설정 묶음: 작은 제목 + 무테 카드 안의 행들 */
@Composable
fun PapySettingsSection(
    title: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PapySpacing.sm)) {
        title?.let {
            Text(
                text = it,
                modifier = Modifier.padding(horizontal = PapySpacing.xs).semantics { heading() },
                style = PapyTheme.typography.bodyStrong,
                color = PapyTheme.colors.onSurfaceVariant,
            )
        }
        PapyCard(contentPadding = PaddingValues(0.dp), verticalArrangement = Arrangement.Top, content = content)
    }
}

/**
 * 설정 한 줄: 제목 + 오른쪽 값(+ 누를 수 있으면 화살표). [destructive] 는 로그아웃처럼 위험한 동작.
 */
@Composable
fun PapySettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    destructive: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = PapySize.controlHeight)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = PapySpacing.xl, vertical = PapySpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PapySpacing.sm),
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = PapyTheme.typography.body,
            color = if (destructive) PapyTheme.colors.error else PapyTheme.colors.onSurface,
        )
        value?.let { Text(text = it, style = PapyTheme.typography.body, color = PapyTheme.colors.onSurfaceVariant) }
        if (onClick != null && !destructive) {
            Icon(
                painter = painterResource(R.drawable.papy_ic_chevron_right),
                contentDescription = null,
                modifier = Modifier.size(PapySize.icon),
                tint = PapyTheme.colors.onSurfaceTertiary,
            )
        }
    }
}

/** 설정 한 줄 스위치(알림 종류·앱 잠금): 제목(+설명) + 오른쪽 스위치. 줄 전체를 눌러 바꾼다. iOS `PapySettingsToggleRow` */
@Composable
fun PapySettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
) {
    PapySwitch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        label = title,
        description = description,
        enabled = enabled,
        modifier = modifier.padding(horizontal = PapySpacing.xl, vertical = PapySpacing.sm),
    )
}

/** 설정 행 사이 구분선 */
@Composable
fun PapySettingsDivider() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = PapySpacing.xl), color = PapyTheme.colors.divider)
}

/** 하나를 고르는 바텀시트 (테마·언어 등). 고르면 [onSelect] 후 닫힌다 */
@Composable
fun <T> PapyOptionSheet(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    PapyBottomSheet(onDismiss = onDismiss, title = title) {
        options.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = PapySize.controlHeight)
                    .clickable {
                        onSelect(option)
                        onDismiss()
                    }
                    .padding(horizontal = PapySpacing.xl),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = label(option), modifier = Modifier.weight(1f), style = PapyTheme.typography.body)
                if (option == selected) {
                    Icon(
                        painter = painterResource(R.drawable.papy_ic_check),
                        contentDescription = null,
                        modifier = Modifier.size(PapySize.icon),
                        tint = PapyTheme.colors.primary,
                    )
                }
            }
        }
    }
}
