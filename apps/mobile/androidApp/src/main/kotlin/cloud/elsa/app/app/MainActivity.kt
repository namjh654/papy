package cloud.elsa.app.app

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import cloud.elsa.app.security.AppLockGate
import cloud.elsa.app.security.CaptureProtection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cloud.elsa.app.designsystem.theme.ElsaTheme
import cloud.elsa.app.core.data.permission.AndroidPermissionController
import cloud.elsa.app.core.data.permission.PermissionController
import cloud.elsa.app.core.data.push.PushManager
import cloud.elsa.app.core.data.settings.AppSettingsRepository
import cloud.elsa.app.navigation.AndroidNavHost
import cloud.elsa.app.push.pushData
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject
import org.koin.core.context.GlobalContext

/** FragmentActivity: 앱 잠금의 BiometricPrompt 가 필요로 한다(ComponentActivity 의 하위라 나머지는 같다) */
class MainActivity : FragmentActivity() {

    private val pushManager: PushManager by inject()

    private val appSettings: AppSettingsRepository by inject()

    /**
     * 설정 탭의 언어·테마를 Activity 구성에 적용한다 → 윈도우 테마와 본문이 같은 기준.
     * 언어를 바꾸면 SettingsScreen 이, 테마를 바꾸면 아래 LaunchedEffect 가 Activity 를 다시 만든다. 저장값을 한 번 읽을 때만 잠깐 기다린다
     */
    override fun attachBaseContext(newBase: Context) {
        val settings = GlobalContext.get().get<AppSettingsRepository>()
        runBlocking { settings.ensureLoaded() }
        super.attachBaseContext(newBase.withAppSettings(settings.language.value, settings.theme.value))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 상태바/내비게이션바 아이콘 색은 시스템 다크 모드에 맞춰 자동 전환
        enableEdgeToEdge()

        super.onCreate(savedInstanceState)

        // 알림을 탭해 앱이 켜졌으면(회전 등 재생성은 제외) data 를 넘긴다 → 루트가 DeepLink 로 이동
        if (savedInstanceState == null) handleIntent(intent)

        // 런타임 권한 창은 Activity 가 띄운다 — 화면이 요청하기 전에 연결(STARTED 전)
        (GlobalContext.get().get<PermissionController>() as? AndroidPermissionController)?.bind(this)

        setContent {
            // 설정 탭의 테마(시스템·라이트·다크) → 다크 여부는 KMP ThemeMode.isDark 한 곳에서 정한다
            val themeMode by appSettings.theme.collectAsStateWithLifecycle()
            val darkTheme = themeMode.isDark(systemDark = isSystemInDarkTheme())
            // 테마를 바꾸면 윈도우(시작 화면·배경)까지 같은 기준으로: Android 12+ 는 시스템이 새 구성으로
            // Activity 를 다시 만들고(다크 여부가 바뀔 때), 그 미만은 직접 다시 만든다(attachBaseContext 가 새 구성)
            var appliedTheme by remember { mutableStateOf(themeMode) }
            LaunchedEffect(themeMode) {
                if (themeMode != appliedTheme) {
                    appliedTheme = themeMode
                    syncAppNightMode(themeMode)
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) recreate()
                }
            }
            val view = LocalView.current
            SideEffect {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }

            ElsaTheme(darkTheme = darkTheme) {
                // 시스템 바 + 키보드(ime) 영역을 루트에서 한 번만 비운다.
                // 키보드가 올라오면 화면이 줄어들고, ElsaFormColumn 이 포커스 필드를 스크롤한다.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ElsaTheme.colors.background)
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                ) {
                    // 화면 캡처 막기(30-2): env security.blockCapture, 앱 잠금이면 최근 앱 화면 가림
                    CaptureProtection()
                    // 앱 잠금(26-1): 잠겨 있으면 화면 전체를 덮는다
                    AppLockGate {
                        AndroidNavHost()
                    }
                }
            }
        }
    }

    /** 앱이 떠 있을 때 알림을 탭하거나 링크를 열면(singleTop) 여기로 온다 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** 외부 링크(elsa://notice/3) 또는 알림 탭(extras) → PushManager.pendingDeepLink → 루트가 메인에서 연다 */
    private fun handleIntent(intent: Intent) {
        intent.dataString?.let(pushManager::openLink) ?: pushManager.onNotificationOpened(intent.pushData())
    }
}
