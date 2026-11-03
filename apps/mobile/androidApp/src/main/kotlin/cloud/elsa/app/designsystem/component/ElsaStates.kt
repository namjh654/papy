package cloud.elsa.app.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import cloud.elsa.app.R
import cloud.elsa.app.designsystem.theme.ElsaTheme
import cloud.elsa.app.designsystem.token.ElsaSize
import cloud.elsa.app.designsystem.token.ElsaSpacing

/** 빈 화면: 가운데 정렬 문구(+ 선택 액션). 목록이 비었을 때 ElsaPagedList 가 쓴다 */
@Composable
fun ElsaEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = ElsaSpacing.section),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ElsaSpacing.sm),
    ) {
        Text(text = title, style = ElsaTheme.typography.subtitle, color = ElsaTheme.colors.onSurface, textAlign = TextAlign.Center)
        message?.let {
            Text(text = it, style = ElsaTheme.typography.body, color = ElsaTheme.colors.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        if (actionText != null && onAction != null) {
            ElsaButton(text = actionText, onClick = onAction, style = ElsaButtonStyle.Secondary, size = ElsaButtonSize.Small, fullWidth = false)
        }
    }
}

/** 전체 오류 화면: 오류 문구 + 다시 시도. 보여줄 내용이 없을 때만 쓴다(내용이 있으면 ElsaInlineMessage) */
@Composable
fun ElsaErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = ElsaSpacing.section),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ElsaSpacing.md),
    ) {
        Icon(
            painter = painterResource(R.drawable.elsa_ic_error),
            contentDescription = null,
            tint = ElsaTheme.colors.onSurfaceTertiary,
            modifier = Modifier.size(ElsaSize.icon * 2),
        )
        Text(text = message, style = ElsaTheme.typography.body, color = ElsaTheme.colors.onSurfaceVariant, textAlign = TextAlign.Center)
        ElsaButton(
            text = stringResource(R.string.common_retry),
            onClick = onRetry,
            style = ElsaButtonStyle.Secondary,
            size = ElsaButtonSize.Small,
            fullWidth = false,
        )
    }
}

/** 오프라인 안내 띠. 루트(AndroidNavHost)가 NetworkMonitor.isOnline 을 보고 화면 위에 한 번만 둔다 */
@Composable
fun ElsaOfflineBanner(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(visible = visible, enter = expandVertically(), exit = shrinkVertically()) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(ElsaTheme.colors.onSurface)
                .padding(horizontal = ElsaSpacing.lg, vertical = ElsaSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(ElsaSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.elsa_ic_offline),
                contentDescription = null,
                tint = ElsaTheme.colors.surface,
                modifier = Modifier.size(ElsaSize.icon),
            )
            Text(text = stringResource(R.string.offline_banner), style = ElsaTheme.typography.bodySmall, color = ElsaTheme.colors.surface)
        }
    }
}
