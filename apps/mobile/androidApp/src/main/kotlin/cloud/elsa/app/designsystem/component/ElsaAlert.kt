package cloud.elsa.app.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import cloud.elsa.app.R
import cloud.elsa.app.designsystem.theme.ElsaTheme
import cloud.elsa.app.designsystem.token.ElsaRadius
import cloud.elsa.app.designsystem.token.ElsaSize
import cloud.elsa.app.designsystem.token.ElsaSpacing

/**
 * 확인/취소 다이얼로그 내용. 화면 상태로 `ElsaAlert?` 를 들고 있다가 null 이 아니면 표시한다.
 * (iOS `.elsaAlert($alert)` 와 같은 사용 방식)
 */
data class ElsaAlert(
    val title: String,
    val message: String? = null,
    /** null 이면 공통 '확인' 문구 */
    val confirmText: String? = null,
    val dismissText: String? = null,
    val isDestructive: Boolean = false,
    val onConfirm: () -> Unit = {},
)

@Composable
fun ElsaAlertDialog(
    alert: ElsaAlert?,
    onDismiss: () -> Unit,
) {
    alert ?: return
    val c = ElsaTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    alert.onConfirm()
                },
            ) {
                Text(
                    text = alert.confirmText ?: stringResource(R.string.common_confirm),
                    style = ElsaTheme.typography.button,
                    color = if (alert.isDestructive) c.error else c.primary,
                )
            }
        },
        dismissButton = alert.dismissText?.let { text ->
            {
                TextButton(onClick = onDismiss) {
                    Text(text = text, style = ElsaTheme.typography.button, color = c.onSurfaceVariant)
                }
            }
        },
        title = { Text(text = alert.title, style = ElsaTheme.typography.title) },
        text = alert.message?.let { message ->
            { Text(text = message, style = ElsaTheme.typography.body) }
        },
        shape = RoundedCornerShape(ElsaRadius.xl),
        containerColor = c.surface,
        titleContentColor = c.onSurface,
        textContentColor = c.onSurfaceVariant,
    )
}

/** 화면 안에 고정 표시하는 알림 박스 (폼 에러 요약, 안내 문구 등). */
@Composable
fun ElsaInlineMessage(
    message: String,
    modifier: Modifier = Modifier,
    type: ElsaMessageType = ElsaMessageType.Info,
    title: String? = null,
) {
    val colors = type.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(ElsaRadius.md),
        color = colors.container,
        contentColor = colors.onContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = ElsaSpacing.lg, vertical = ElsaSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(ElsaSpacing.sm),
            verticalAlignment = if (title == null) Alignment.CenterVertically else Alignment.Top,
        ) {
            Icon(
                painter = painterResource(type.icon),
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(ElsaSize.icon),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(ElsaSpacing.xxs),
            ) {
                title?.let { Text(text = it, style = ElsaTheme.typography.subtitle) }
                Text(text = message, style = ElsaTheme.typography.bodySmall)
            }
        }
    }
}
