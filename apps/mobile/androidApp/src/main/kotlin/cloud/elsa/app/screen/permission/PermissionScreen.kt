package cloud.elsa.app.screen.permission

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cloud.elsa.app.R
import cloud.elsa.app.component.EventEffect
import cloud.elsa.app.core.data.permission.AppPermission
import cloud.elsa.app.core.data.permission.PermissionStatus
import cloud.elsa.app.designsystem.component.ElsaAlert
import cloud.elsa.app.designsystem.component.ElsaAlertDialog
import cloud.elsa.app.designsystem.component.ElsaBadge
import cloud.elsa.app.designsystem.component.ElsaButton
import cloud.elsa.app.designsystem.component.ElsaButtonSize
import cloud.elsa.app.designsystem.component.ElsaButtonStyle
import cloud.elsa.app.designsystem.component.ElsaCard
import cloud.elsa.app.designsystem.component.ElsaMessageType
import cloud.elsa.app.designsystem.component.ElsaScaffold
import cloud.elsa.app.designsystem.component.ElsaScreenHeader
import cloud.elsa.app.designsystem.component.ElsaScrollScreen
import cloud.elsa.app.designsystem.theme.ElsaTheme
import cloud.elsa.app.designsystem.token.ElsaSpacing
import cloud.elsa.app.feature.permission.PermissionEvent
import cloud.elsa.app.feature.permission.PermissionViewModel
import org.koin.androidx.compose.koinViewModel

/**
 * 권한 요청 기준 화면: 권한마다 "왜 필요한지" + 현재 상태 + 상태에 맞는 버튼.
 * 화면이 다시 보일 때마다(설정 앱·권한 창에서 돌아올 때) 상태를 다시 읽는다.
 */
@Composable
fun PermissionScreen(
    onBack: () -> Unit,
) {
    val viewModel: PermissionViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var alert by remember { mutableStateOf<ElsaAlert?>(null) }

    // 재확인: 처음 보일 때 + 다른 앱(설정)·시스템 창에서 돌아올 때마다
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val settingsTitle = stringResource(R.string.permission_settings_title)
    val settingsMessage = stringResource(R.string.permission_settings_message)
    val openSettingsText = stringResource(R.string.permission_open_settings)
    val cancelText = stringResource(R.string.common_cancel)

    EventEffect(viewModel.events) { event ->
        when (event) {
            is PermissionEvent.SettingsRequired -> alert = ElsaAlert(
                title = settingsTitle,
                message = settingsMessage,
                confirmText = openSettingsText,
                dismissText = cancelText,
                onConfirm = viewModel::openSettings,
            )
        }
    }

    ElsaScaffold(onBack = onBack, containerColor = ElsaTheme.colors.backgroundGrouped) { padding ->
        ElsaScrollScreen(modifier = Modifier.padding(padding)) {
            ElsaScreenHeader(
                title = stringResource(R.string.permission_title),
                description = stringResource(R.string.permission_description),
            )

            AppPermission.entries.forEach { permission ->
                val status = state.status(permission)
                ElsaCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ElsaSpacing.sm)) {
                        Text(
                            text = permission.title(),
                            style = ElsaTheme.typography.subtitle,
                            color = ElsaTheme.colors.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        status?.let { ElsaBadge(text = it.label(), type = it.badgeType()) }
                    }
                    Text(
                        text = permission.reason(),
                        style = ElsaTheme.typography.bodySmall,
                        color = ElsaTheme.colors.onSurfaceVariant,
                    )
                    if (status != null && !status.isGranted) {
                        ElsaButton(
                            text = stringResource(
                                if (status == PermissionStatus.PermanentlyDenied) R.string.permission_open_settings
                                else R.string.permission_allow,
                            ),
                            onClick = { viewModel.request(permission) },
                            style = ElsaButtonStyle.Secondary,
                            size = ElsaButtonSize.Small,
                            isLoading = state.requesting == permission,
                        )
                    }
                }
            }
        }
    }

    ElsaAlertDialog(alert = alert, onDismiss = { alert = null })
}

@Composable
private fun AppPermission.title(): String = stringResource(
    when (this) {
        AppPermission.Notifications -> R.string.permission_notifications
        AppPermission.Camera -> R.string.permission_camera
        AppPermission.Location -> R.string.permission_location
    },
)

/** 왜 필요한지 — 시스템 창을 띄우기 전에 먼저 보여준다 */
@Composable
private fun AppPermission.reason(): String = stringResource(
    when (this) {
        AppPermission.Notifications -> R.string.permission_notifications_reason
        AppPermission.Camera -> R.string.permission_camera_reason
        AppPermission.Location -> R.string.permission_location_reason
    },
)

@Composable
private fun PermissionStatus.label(): String = stringResource(
    when (this) {
        PermissionStatus.Granted -> R.string.permission_status_granted
        PermissionStatus.NotRequested -> R.string.permission_status_not_requested
        PermissionStatus.Denied, PermissionStatus.PermanentlyDenied -> R.string.permission_status_denied
    },
)

private fun PermissionStatus.badgeType(): ElsaMessageType = when (this) {
    PermissionStatus.Granted -> ElsaMessageType.Success
    PermissionStatus.NotRequested -> ElsaMessageType.Info
    PermissionStatus.Denied, PermissionStatus.PermanentlyDenied -> ElsaMessageType.Warning
}
