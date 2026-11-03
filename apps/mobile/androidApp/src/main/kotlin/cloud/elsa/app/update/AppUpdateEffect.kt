package cloud.elsa.app.update

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cloud.elsa.app.R
import cloud.elsa.app.core.data.update.AppUpdateManager
import cloud.elsa.app.core.data.update.AppUpdateStatus
import cloud.elsa.app.designsystem.component.ElsaAlert
import cloud.elsa.app.designsystem.component.ElsaAlertDialog
import org.koin.compose.koinInject

/**
 * 앱 시작 때 한 번 업데이트를 확인하고 안내한다(루트에 한 번). 강제 업데이트는 닫을 수 없다.
 * 업데이트 안내가 없으면 운영 공지(DEC-132)를 보인다 — 점검(blocking)은 닫을 수 없고, 공지는 "확인"으로 닫는다.
 * iOS 는 AppNavigation 의 `.appUpdateAlert`.
 */
@Composable
fun AppUpdateEffect(
    manager: AppUpdateManager = koinInject(),
    updater: StoreUpdater = remember { PlayStoreUpdater() },
) {
    LaunchedEffect(Unit) { manager.check() }
    val status by manager.status.collectAsStateWithLifecycle()
    val notice by manager.notice.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? Activity

    val updateText = stringResource(R.string.update_button)
    val alert = when (val s = status) {
        AppUpdateStatus.UpToDate -> notice?.let { n ->
            ElsaAlert(
                title = n.title ?: stringResource(if (n.blocking) R.string.app_notice_maintenance_title else R.string.app_notice_title),
                message = n.message,
                // 점검은 확인해도 닫히지 않는다(상태가 그대로라 다시 보인다)
                onConfirm = manager::dismissNotice,
            )
        }
        is AppUpdateStatus.Required -> ElsaAlert(
            title = stringResource(R.string.update_required_title),
            message = stringResource(R.string.update_required_message),
            confirmText = updateText,
            onConfirm = { activity?.let { updater.startUpdate(it, required = true, storeUrl = s.storeUrl) } },
        )
        is AppUpdateStatus.Optional -> ElsaAlert(
            title = stringResource(R.string.update_optional_title),
            message = stringResource(R.string.update_optional_message, s.latestVersion),
            confirmText = updateText,
            dismissText = stringResource(R.string.update_later),
            onConfirm = {
                manager.dismissOptional()
                activity?.let { updater.startUpdate(it, required = false, storeUrl = s.storeUrl) }
            },
        )
    }
    ElsaAlertDialog(
        alert = alert,
        // 강제 업데이트는 바깥을 눌러도 닫히지 않는다(상태가 그대로라 다시 보인다)
        onDismiss = {
            if (status is AppUpdateStatus.Optional) manager.dismissOptional()
            if (status is AppUpdateStatus.UpToDate) manager.dismissNotice()
        },
    )
}
