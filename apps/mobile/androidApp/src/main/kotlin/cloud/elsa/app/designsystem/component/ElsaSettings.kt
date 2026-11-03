package cloud.elsa.app.designsystem.component

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
import cloud.elsa.app.R
import cloud.elsa.app.designsystem.theme.ElsaTheme
import cloud.elsa.app.designsystem.token.ElsaSize
import cloud.elsa.app.designsystem.token.ElsaSpacing

/** 설정 묶음: 작은 제목 + 무테 카드 안의 행들 */
@Composable
fun ElsaSettingsSection(
    title: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(ElsaSpacing.sm)) {
        title?.let {
            Text(
                text = it,
                modifier = Modifier.padding(horizontal = ElsaSpacing.xs).semantics { heading() },
                style = ElsaTheme.typography.bodyStrong,
                color = ElsaTheme.colors.onSurfaceVariant,
            )
        }
        ElsaCard(contentPadding = PaddingValues(0.dp), verticalArrangement = Arrangement.Top, content = content)
    }
}

/**
 * 설정 한 줄: 제목 + 오른쪽 값(+ 누를 수 있으면 화살표). [destructive] 는 로그아웃처럼 위험한 동작.
 */
@Composable
fun ElsaSettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    destructive: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ElsaSize.controlHeight)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = ElsaSpacing.xl, vertical = ElsaSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ElsaSpacing.sm),
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = ElsaTheme.typography.body,
            color = if (destructive) ElsaTheme.colors.error else ElsaTheme.colors.onSurface,
        )
        value?.let { Text(text = it, style = ElsaTheme.typography.body, color = ElsaTheme.colors.onSurfaceVariant) }
        if (onClick != null && !destructive) {
            Icon(
                painter = painterResource(R.drawable.elsa_ic_chevron_right),
                contentDescription = null,
                modifier = Modifier.size(ElsaSize.icon),
                tint = ElsaTheme.colors.onSurfaceTertiary,
            )
        }
    }
}

/** 설정 한 줄 스위치(알림 종류·앱 잠금): 제목(+설명) + 오른쪽 스위치. 줄 전체를 눌러 바꾼다. iOS `ElsaSettingsToggleRow` */
@Composable
fun ElsaSettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
) {
    ElsaSwitch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        label = title,
        description = description,
        enabled = enabled,
        modifier = modifier.padding(horizontal = ElsaSpacing.xl, vertical = ElsaSpacing.sm),
    )
}

/** 설정 행 사이 구분선 */
@Composable
fun ElsaSettingsDivider() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = ElsaSpacing.xl), color = ElsaTheme.colors.divider)
}

/** 하나를 고르는 바텀시트 (테마·언어 등). 고르면 [onSelect] 후 닫힌다 */
@Composable
fun <T> ElsaOptionSheet(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    ElsaBottomSheet(onDismiss = onDismiss, title = title) {
        options.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ElsaSize.controlHeight)
                    .clickable {
                        onSelect(option)
                        onDismiss()
                    }
                    .padding(horizontal = ElsaSpacing.xl),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = label(option), modifier = Modifier.weight(1f), style = ElsaTheme.typography.body)
                if (option == selected) {
                    Icon(
                        painter = painterResource(R.drawable.elsa_ic_check),
                        contentDescription = null,
                        modifier = Modifier.size(ElsaSize.icon),
                        tint = ElsaTheme.colors.primary,
                    )
                }
            }
        }
    }
}
