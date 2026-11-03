package io.github.namjh654.papy.push

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import io.github.namjh654.papy.core.data.push.PushManager
import io.github.namjh654.papy.core.data.push.PushPermission
import io.github.namjh654.papy.core.data.session.AuthStatus
import io.github.namjh654.papy.navigation.isInMainGraph
import io.github.namjh654.papy.navigation.openDeepLink
import org.koin.compose.koinInject

/**
 * 앱 루트에서 한 번 호출한다.
 * - 로그인 상태가 되면 알림 권한을 요청한다(Android 13+, 푸시가 켜져 있을 때만)
 * - 알림 탭으로 받은 DeepLink 를 메인 화면에 들어온 뒤 연다(콜드 스타트 포함)
 */
@Composable
fun PushEffects(navController: NavController, authStatus: AuthStatus) {
    val pushManager: PushManager = koinInject()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pushManager.onPermissionChanged(if (granted) PushPermission.Granted else PushPermission.Denied)
    }
    LaunchedEffect(authStatus) {
        if (authStatus != AuthStatus.Authenticated || !pushManager.provider.isEnabled) return@LaunchedEffect
        val needsRequest = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsRequest) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            val enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
            pushManager.onPermissionChanged(if (enabled) PushPermission.Granted else PushPermission.Denied)
        }
    }

    val pendingDeepLink by pushManager.pendingDeepLink.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(pendingDeepLink, backStackEntry) {
        val deepLink = pendingDeepLink ?: return@LaunchedEffect
        if (!navController.isInMainGraph()) return@LaunchedEffect // 로그인·세션 복원 뒤 메인에 들어오면 연다
        navController.openDeepLink(deepLink)
        pushManager.consumeDeepLink()
    }
}
