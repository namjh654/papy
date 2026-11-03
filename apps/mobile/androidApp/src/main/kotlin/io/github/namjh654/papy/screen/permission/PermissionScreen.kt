package io.github.namjh654.papy.screen.permission

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
import io.github.namjh654.papy.R
import io.github.namjh654.papy.component.EventEffect
import io.github.namjh654.papy.core.data.permission.AppPermission
import io.github.namjh654.papy.core.data.permission.PermissionStatus
import io.github.namjh654.papy.designsystem.component.PapyAlert
import io.github.namjh654.papy.designsystem.component.PapyAlertDialog
import io.github.namjh654.papy.designsystem.component.PapyBadge
import io.github.namjh654.papy.designsystem.component.PapyButton
import io.github.namjh654.papy.designsystem.component.PapyButtonSize
import io.github.namjh654.papy.designsystem.component.PapyButtonStyle
import io.github.namjh654.papy.designsystem.component.PapyCard
import io.github.namjh654.papy.designsystem.component.PapyMessageType
import io.github.namjh654.papy.designsystem.component.PapyScaffold
import io.github.namjh654.papy.designsystem.component.PapyScreenHeader
import io.github.namjh654.papy.designsystem.component.PapyScrollScreen
import io.github.namjh654.papy.designsystem.theme.PapyTheme
import io.github.namjh654.papy.designsystem.token.PapySpacing
import io.github.namjh654.papy.feature.permission.PermissionEvent
import io.github.namjh654.papy.feature.permission.PermissionViewModel
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
    var alert by remember { mutableStateOf<PapyAlert?>(null) }

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
            is PermissionEvent.SettingsRequired -> alert = PapyAlert(
                title = settingsTitle,
                message = settingsMessage,
                confirmText = openSettingsText,
                dismissText = cancelText,
                onConfirm = viewModel::openSettings,
            )
        }
    }

    PapyScaffold(onBack = onBack, containerColor = PapyTheme.colors.backgroundGrouped) { padding ->
        PapyScrollScreen(modifier = Modifier.padding(padding)) {
            PapyScreenHeader(
                title = stringResource(R.string.permission_title),
                description = stringResource(R.string.permission_description),
            )

            AppPermission.entries.forEach { permission ->
                val status = state.status(permission)
                PapyCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PapySpacing.sm)) {
                        Text(
                            text = permission.title(),
                            style = PapyTheme.typography.subtitle,
                            color = PapyTheme.colors.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        status?.let { PapyBadge(text = it.label(), type = it.badgeType()) }
                    }
                    Text(
                        text = permission.reason(),
                        style = PapyTheme.typography.bodySmall,
                        color = PapyTheme.colors.onSurfaceVariant,
                    )
                    if (status != null && !status.isGranted) {
                        PapyButton(
                            text = stringResource(
                                if (status == PermissionStatus.PermanentlyDenied) R.string.permission_open_settings
                                else R.string.permission_allow,
                            ),
                            onClick = { viewModel.request(permission) },
                            style = PapyButtonStyle.Secondary,
                            size = PapyButtonSize.Small,
                            isLoading = state.requesting == permission,
                        )
                    }
                }
            }
        }
    }

    PapyAlertDialog(alert = alert, onDismiss = { alert = null })
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

private fun PermissionStatus.badgeType(): PapyMessageType = when (this) {
    PermissionStatus.Granted -> PapyMessageType.Success
    PermissionStatus.NotRequested -> PapyMessageType.Info
    PermissionStatus.Denied, PermissionStatus.PermanentlyDenied -> PapyMessageType.Warning
}
