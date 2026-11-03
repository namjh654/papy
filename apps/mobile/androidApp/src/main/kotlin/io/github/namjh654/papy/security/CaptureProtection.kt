package io.github.namjh654.papy.security

import android.app.Activity
import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.namjh654.papy.core.data.config.AppConfig
import io.github.namjh654.papy.core.data.security.AppLock
import org.koin.compose.koinInject

/**
 * 화면 캡처 막기(30-2, DEC-137). 루트에 한 번.
 * - env `security.blockCapture=true`: 앱 전체 FLAG_SECURE — 스크린샷·화면 녹화가 검게 나오고 최근 앱 화면도 가려진다
 * - 앱 잠금을 켰으면: 최근 앱 화면만 가린다(Android 13+ setRecentsScreenshotEnabled, 그 아래는 FLAG_SECURE)
 * iOS 는 스크린샷을 막을 수 없어 녹화·미러링 중 가림 + 스크린샷 안내(`security/capture.protection.swift`).
 */
@Composable
fun CaptureProtection(appLock: AppLock = koinInject()) {
    val activity = LocalContext.current as? Activity ?: return
    val lockEnabled by appLock.isEnabled.collectAsStateWithLifecycle()
    val blockAll = AppConfig.SECURITY_BLOCK_CAPTURE

    DisposableEffect(lockEnabled, blockAll) {
        val hideRecentsOnly = lockEnabled && !blockAll && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        val secure = blockAll || (lockEnabled && !hideRecentsOnly)
        if (secure) activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) activity.setRecentsScreenshotEnabled(!hideRecentsOnly)
        onDispose { }
    }
}
