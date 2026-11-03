package cloud.elsa.app.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActionScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import cloud.elsa.app.R
import cloud.elsa.app.designsystem.theme.ElsaTheme
import cloud.elsa.app.designsystem.token.ElsaRadius
import cloud.elsa.app.designsystem.token.ElsaSize
import cloud.elsa.app.designsystem.token.ElsaSpacing

/**
 * 공통 텍스트 필드. 라벨은 필드 위, 도움말/에러는 필드 아래에 표시한다 (iOS 와 동일 배치).
 *
 * - [isSecure]: 비밀번호 입력 + 보기/숨기기 버튼
 * - [maxLength]: 초과 입력 차단 + 글자 수 표시
 * - [imeAction]: 기본 Next(다음 필드로 이동). 마지막 필드는 Done 과 [onImeAction] 을 지정한다.
 * - 포커스 시 키보드 위로 자동 스크롤된다 (ElsaFormColumn 안에서).
 */
@Composable
fun ElsaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    helperText: String? = null,
    errorText: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isSecure: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLength: Int? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null,
    @DrawableRes leadingIcon: Int? = null,
) {
    val c = ElsaTheme.colors
    val focusManager = LocalFocusManager.current
    var revealed by rememberSaveable { mutableStateOf(false) }
    val isError = errorText != null
    val imeHandler: (KeyboardActionScope.() -> Unit)? = if (onImeAction != null) {
        { onImeAction() }
    } else {
        null
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ElsaSpacing.xs),
    ) {
        label?.let { ElsaFieldLabel(it, enabled) }

        OutlinedTextField(
            value = value,
            onValueChange = { next ->
                if (maxLength == null || next.length <= maxLength) onValueChange(next)
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ElsaSize.controlHeight)
                .bringIntoViewOnFocus(),
            enabled = enabled,
            readOnly = readOnly,
            textStyle = ElsaTheme.typography.body,
            placeholder = placeholder?.let { { Text(it, style = ElsaTheme.typography.body) } },
            leadingIcon = leadingIcon?.let {
                { Icon(painterResource(it), contentDescription = null, modifier = Modifier.size(ElsaSize.icon)) }
            },
            trailingIcon = if (isSecure) {
                {
                    IconButton(onClick = { revealed = !revealed }) {
                        Icon(
                            painter = painterResource(
                                if (revealed) R.drawable.elsa_ic_visibility_off else R.drawable.elsa_ic_visibility,
                            ),
                            contentDescription = stringResource(
                                if (revealed) R.string.field_password_hide else R.string.field_password_show,
                            ),
                            modifier = Modifier.size(ElsaSize.icon),
                        )
                    }
                }
            } else {
                null
            },
            isError = isError,
            visualTransformation = if (isSecure && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isSecure) KeyboardType.Password else keyboardType,
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    onImeAction?.invoke()
                },
                onGo = imeHandler,
                onSearch = imeHandler,
                onSend = imeHandler,
            ),
            singleLine = singleLine,
            minLines = if (singleLine) 1 else minLines,
            shape = RoundedCornerShape(ElsaRadius.md),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = c.onSurface,
                unfocusedTextColor = c.onSurface,
                disabledTextColor = c.onDisabled,
                focusedContainerColor = c.fill,
                unfocusedContainerColor = c.fill,
                disabledContainerColor = c.disabled,
                errorContainerColor = c.fill,
                cursorColor = c.primary,
                errorCursorColor = c.error,
                focusedBorderColor = c.primary,
                unfocusedBorderColor = Color.Transparent,
                disabledBorderColor = Color.Transparent,
                errorBorderColor = c.error,
                focusedLeadingIconColor = c.onSurfaceVariant,
                unfocusedLeadingIconColor = c.onSurfaceVariant,
                focusedTrailingIconColor = c.onSurfaceVariant,
                unfocusedTrailingIconColor = c.onSurfaceVariant,
                errorTrailingIconColor = c.error,
                focusedPlaceholderColor = c.onSurfaceTertiary,
                unfocusedPlaceholderColor = c.onSurfaceTertiary,
            ),
        )

        ElsaFieldFooter(
            helperText = helperText,
            errorText = errorText,
            counter = maxLength?.let { "${value.length}/$it" },
        )
    }
}

/**
 * 탭하면 선택 UI(날짜/시간/목록 등)를 여는 읽기 전용 필드. ElsaTextField 와 같은 모양이다.
 */
@Composable
fun ElsaSelectField(
    value: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    helperText: String? = null,
    errorText: String? = null,
    enabled: Boolean = true,
    @DrawableRes trailingIcon: Int? = null,
) {
    val c = ElsaTheme.colors
    val isError = errorText != null

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ElsaSpacing.xs),
    ) {
        label?.let { ElsaFieldLabel(it, enabled) }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ElsaSize.controlHeight),
            shape = RoundedCornerShape(ElsaRadius.md),
            color = if (enabled) c.fill else c.disabled,
            border = if (isError) BorderStroke(ElsaSize.border, c.error) else null,
        ) {
            Row(
                modifier = Modifier
                    .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                    .heightIn(min = ElsaSize.controlHeight)
                    .padding(horizontal = ElsaSpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = value ?: placeholder ?: stringResource(R.string.common_select),
                    modifier = Modifier.weight(1f),
                    style = ElsaTheme.typography.body,
                    color = when {
                        !enabled -> c.onDisabled
                        value == null -> c.onSurfaceTertiary
                        else -> c.onSurface
                    },
                )
                trailingIcon?.let {
                    Icon(
                        painter = painterResource(it),
                        contentDescription = null,
                        modifier = Modifier.size(ElsaSize.icon),
                        tint = c.onSurfaceVariant,
                    )
                }
            }
        }

        ElsaFieldFooter(helperText = helperText, errorText = errorText, counter = null)
    }
}

@Composable
internal fun ElsaFieldLabel(text: String, enabled: Boolean = true) {
    Text(
        text = text,
        style = ElsaTheme.typography.label,
        color = if (enabled) ElsaTheme.colors.onSurfaceVariant else ElsaTheme.colors.onDisabled,
    )
}

@Composable
internal fun ElsaFieldFooter(helperText: String?, errorText: String?, counter: String?) {
    val message = errorText ?: helperText
    if (message == null && counter == null) return
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = message.orEmpty(),
            modifier = Modifier.weight(1f),
            style = ElsaTheme.typography.caption,
            color = if (errorText != null) ElsaTheme.colors.error else ElsaTheme.colors.onSurfaceVariant,
        )
        if (counter != null) {
            Spacer(Modifier.size(ElsaSpacing.sm))
            Text(
                text = counter,
                style = ElsaTheme.typography.caption,
                color = ElsaTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.End,
            )
        }
    }
}
