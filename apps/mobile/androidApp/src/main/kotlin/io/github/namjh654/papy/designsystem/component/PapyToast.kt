package io.github.namjh654.papy.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.namjh654.papy.designsystem.theme.PapyTheme
import io.github.namjh654.papy.designsystem.token.PapyRadius
import io.github.namjh654.papy.designsystem.token.PapySize
import io.github.namjh654.papy.designsystem.token.PapySpacing

/**
 * 잠깐 보였다 사라지는 알림(토스트/스낵바).
 *
 * ```
 * val toast = rememberPapyToastState()
 * PapyScaffold(toastState = toast) { ... }
 * scope.launch { toast.show("저장했습니다", PapyMessageType.Success) }
 * ```
 */
@Stable
class PapyToastState {
    internal val hostState = SnackbarHostState()

    /** @return 액션 버튼을 눌렀으면 true. 이전 토스트는 즉시 닫힌다. */
    suspend fun show(
        message: String,
        type: PapyMessageType = PapyMessageType.Info,
        actionLabel: String? = null,
    ): Boolean {
        hostState.currentSnackbarData?.dismiss()
        val result = hostState.showSnackbar(PapyToastVisuals(message, type, actionLabel))
        return result == SnackbarResult.ActionPerformed
    }
}

@Composable
fun rememberPapyToastState(): PapyToastState = remember { PapyToastState() }

private class PapyToastVisuals(
    override val message: String,
    val type: PapyMessageType,
    override val actionLabel: String?,
) : SnackbarVisuals {
    override val withDismissAction: Boolean = false
    override val duration: SnackbarDuration =
        if (actionLabel == null) SnackbarDuration.Short else SnackbarDuration.Long
}

@Composable
fun PapyToastHost(
    state: PapyToastState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(hostState = state.hostState, modifier = modifier) { data ->
        val type = (data.visuals as? PapyToastVisuals)?.type ?: PapyMessageType.Info
        val colors = type.colors
        val c = PapyTheme.colors
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PapySpacing.lg, vertical = PapySpacing.sm),
            shape = RoundedCornerShape(PapyRadius.md),
            // 어떤 배경 위에서도 보이도록 반전 색(onSurface/surface) 사용, 종류는 아이콘 색으로 구분
            color = c.onSurface,
            contentColor = c.surface,
            shadowElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier.padding(start = PapySpacing.lg, end = PapySpacing.sm, top = PapySpacing.md, bottom = PapySpacing.md),
                horizontalArrangement = Arrangement.spacedBy(PapySpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(type.icon),
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(PapySize.icon),
                )
                Text(
                    text = data.visuals.message,
                    modifier = Modifier.weight(1f),
                    style = PapyTheme.typography.bodySmall,
                )
                data.visuals.actionLabel?.let { action ->
                    TextButton(onClick = data::performAction) {
                        Text(text = action, style = PapyTheme.typography.label, color = c.primaryContainer)
                    }
                }
            }
        }
    }
}
