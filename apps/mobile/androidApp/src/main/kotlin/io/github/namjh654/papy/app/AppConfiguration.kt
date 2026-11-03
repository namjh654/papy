package io.github.namjh654.papy.app

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import io.github.namjh654.papy.core.data.settings.AppLanguage
import io.github.namjh654.papy.core.data.settings.ThemeMode
import java.util.Locale

/**
 * 설정 탭의 언어·테마를 Activity 구성에 입힌다(모든 API 레벨, 라이브러리 없이). MainActivity.attachBaseContext 에서 쓴다.
 *
 * - 언어: [AppLanguage.System] 이면 기기(또는 Android 13+ 앱별 언어) 설정 그대로
 * - 테마: [ThemeMode.forcedDark] 가 있으면 uiMode 의 야간 비트를 바꾼다 → 윈도우 테마(values-night)·
 *   `isSystemInDarkTheme()`·Compose 가 모두 같은 기준(ThemeMode.isDark)으로 그린다. System 이면 그대로
 */
fun Context.withAppSettings(language: AppLanguage, theme: ThemeMode): Context {
    val tag = language.tag
    val forcedDark = theme.forcedDark
    if (tag == null && forcedDark == null) return this

    val config = Configuration(resources.configuration)
    tag?.let {
        val locale = Locale.forLanguageTag(it)
        Locale.setDefault(locale)
        config.setLocales(LocaleList(locale))
    }
    forcedDark?.let { dark ->
        val night = if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
    }
    return createConfigurationContext(config)
}

/**
 * Android 12+ 는 앱을 띄우기 전 시스템이 그리는 시작 화면(스플래시 창)도 앱 테마로 맞춘다.
 * 시스템이 앱별로 기억하므로 앱 시작 때 저장값으로 한 번 맞추고(데이터 초기화·백업 복원 뒤 어긋남 방지), 바꿀 때마다 부른다.
 * 값이 바뀌면 시스템이 Activity 를 다시 만든다. 12 미만은 아무것도 하지 않는다(시작 화면은 시스템 기준).
 */
fun Context.syncAppNightMode(theme: ThemeMode) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val manager = getSystemService(UiModeManager::class.java) ?: return
    val mode = when (theme.forcedDark) {
        null -> UiModeManager.MODE_NIGHT_AUTO
        true -> UiModeManager.MODE_NIGHT_YES
        false -> UiModeManager.MODE_NIGHT_NO
    }
    manager.setApplicationNightMode(mode)
}
