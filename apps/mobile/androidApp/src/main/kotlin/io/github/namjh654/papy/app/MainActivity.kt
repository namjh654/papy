package io.github.namjh654.papy.app

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.namjh654.papy.core.data.settings.AppSettingsRepository
import io.github.namjh654.papy.designsystem.theme.PapyTheme
import io.github.namjh654.papy.screen.documents.DocumentsNavHost
import io.github.namjh654.papy.screen.documents.IncomingDocuments
import io.github.namjh654.papy.screen.documents.displayName
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject
import org.koin.core.context.GlobalContext

/** Papy 문서 앱 진입점: 최근 문서 → 문서 보기(DocumentsNavHost). 다른 앱의 "Papy 로 열기"도 여기로 온다. */
class MainActivity : ComponentActivity() {

    private val appSettings: AppSettingsRepository by inject()

    /** 저장된 언어·테마를 Activity 구성에 입힌다(없으면 시스템). 저장값을 한 번 읽을 때만 잠깐 기다린다 */
    override fun attachBaseContext(newBase: Context) {
        val settings = GlobalContext.get().get<AppSettingsRepository>()
        runBlocking { settings.ensureLoaded() }
        super.attachBaseContext(newBase.withAppSettings(settings.language.value, settings.theme.value))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 상태바/내비게이션바 아이콘 색은 시스템 다크 모드에 맞춰 자동 전환
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // 다른 앱에서 문서를 열어 앱이 켜졌으면(회전 등 재생성은 제외) 그 문서를 연다
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            val themeMode by appSettings.theme.collectAsStateWithLifecycle()
            val darkTheme = themeMode.isDark(systemDark = isSystemInDarkTheme())
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

            PapyTheme(darkTheme = darkTheme) {
                // 시스템 바 + 키보드(ime) 영역을 루트에서 한 번만 비운다
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(PapyTheme.colors.background)
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                ) {
                    DocumentsNavHost()
                }
            }
        }
    }

    /** 앱이 떠 있을 때 다른 앱에서 문서를 열면(singleTop) 여기로 온다 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action != Intent.ACTION_VIEW || intent.data?.scheme != "content") return
        val uri = intent.data ?: return
        IncomingDocuments.offer(uri, displayName(uri))
    }
}
