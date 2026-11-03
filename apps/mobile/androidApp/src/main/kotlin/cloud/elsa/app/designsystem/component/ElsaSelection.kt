package cloud.elsa.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cloud.elsa.app.R
import cloud.elsa.app.designsystem.theme.ElsaTheme
import cloud.elsa.app.designsystem.token.ElsaSize
import cloud.elsa.app.designsystem.token.ElsaSpacing

private val MinTouchHeight = 48.dp

/** 라벨까지 탭 영역인 체크박스. */
@Composable
fun ElsaCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
) {
    val c = ElsaTheme.colors
    Row(
        modifier = modifier
            .heightIn(min = MinTouchHeight)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ElsaSpacing.sm),
    ) {
        CheckboxBox(checked = checked, enabled = enabled)
        SelectionLabel(label, description, enabled)
    }
}

/**
 * 체크 상자: iOS(`checkmark.square.fill`/`square`)와 같은 둥근 사각형 `size.checkbox`.
 * 크기는 sp 로 잡아 글자 크기 설정이 커져도 라벨과 비례한다(Material Checkbox 는 고정 크기라 큰 글자에서 상자만 작아 보였다).
 */
@Composable
private fun CheckboxBox(checked: Boolean, enabled: Boolean) {
    val c = ElsaTheme.colors
    val size = with(LocalDensity.current) { ElsaSize.checkbox.value.sp.toDp() }
    val shape = RoundedCornerShape(size * 0.22f)
    val color = when {
        !enabled -> c.onDisabled
        checked -> c.primary
        else -> c.outlineStrong
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .then(if (checked) Modifier.background(color) else Modifier.border(size * 0.09f, color, shape)),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                painter = painterResource(R.drawable.elsa_ic_check),
                contentDescription = null,
                modifier = Modifier.size(size * 0.72f),
                tint = c.onPrimary,
            )
        }
    }
}

/** 라벨 오른쪽에 스위치가 있는 설정 행. iOS `ElsaToggle` 과 대응한다. */
@Composable
fun ElsaSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
) {
    val c = ElsaTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchHeight)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ElsaSpacing.md),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            SelectionLabel(label, description, enabled)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = c.onPrimary,
                checkedTrackColor = c.primary,
                checkedBorderColor = c.primary,
                uncheckedThumbColor = c.outlineStrong,
                uncheckedTrackColor = c.surfaceVariant,
                uncheckedBorderColor = c.outline,
            ),
        )
    }
}

/** 단일 선택 목록. */
@Composable
fun <T> ElsaRadioGroup(
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    optionLabel: (T) -> String,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
) {
    val c = ElsaTheme.colors
    Column(modifier = modifier.selectableGroup()) {
        label?.let {
            ElsaFieldLabel(it, enabled)
        }
        options.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MinTouchHeight)
                    .selectable(
                        selected = option == selected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onSelect(option) },
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ElsaSpacing.sm),
            ) {
                RadioButton(
                    selected = option == selected,
                    onClick = null,
                    enabled = enabled,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = c.primary,
                        unselectedColor = c.outlineStrong,
                    ),
                )
                SelectionLabel(optionLabel(option), null, enabled)
            }
        }
    }
}

@Composable
private fun SelectionLabel(label: String, description: String?, enabled: Boolean) {
    val c = ElsaTheme.colors
    Column(modifier = Modifier.padding(vertical = ElsaSpacing.xs)) {
        Text(
            text = label,
            style = ElsaTheme.typography.body,
            color = if (enabled) c.onSurface else c.onDisabled,
        )
        description?.let {
            Text(text = it, style = ElsaTheme.typography.caption, color = c.onSurfaceVariant)
        }
    }
}
