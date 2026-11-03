package io.github.namjh654.papy.core.data.settings

import io.github.namjh654.papy.core.data.storage.setting.SettingStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 화면 테마. 기본은 [System](저장값이 없거나 모르는 값이면 시스템을 따른다).
 * 다크 여부는 양 플랫폼이 [isDark] 한 곳으로 정한다 — 시작 화면(윈도우)과 본문이 같은 기준을 쓴다.
 * Swift: `.system` `.light` `.dark`, `mode.forcedDark`(KotlinBoolean?), `mode.isDark(systemDark:)`
 */
enum class ThemeMode {
    System,
    Light,
    Dark,
    ;

    /** 앱이 강제하는 다크 여부. null 이면 시스템을 따른다 */
    val forcedDark: Boolean?
        get() = when (this) {
            System -> null
            Light -> false
            Dark -> true
        }

    /** 이 설정에서 지금 다크로 그릴지 */
    fun isDark(systemDark: Boolean): Boolean = forcedDark ?: systemDark

    companion object {
        /** 저장값 → 설정. 없거나(null·빈 값) 모르는 값이면 [System] */
        fun fromStored(value: String?): ThemeMode =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: System
    }
}

/**
 * 앱 언어. [tag] 가 null 이면 기기 설정을 따른다.
 * 언어를 추가하면 플랫폼 리소스(values-xx, Localizable.xcstrings)와 Android locales_config.xml 도 같이 추가한다.
 */
enum class AppLanguage(val tag: String?) {
    System(null),
    Korean("ko"),
    English("en"),
}

/**
 * 앱 전역 설정(테마·언어). 저장은 [SettingStorage], 루트 화면이 [theme]·[language] 를 관찰해 적용한다.
 * - 테마: Android `PapyTheme(themeMode)`, iOS `.preferredColorScheme`
 * - 언어: Android 는 Activity 컨텍스트 로케일(즉시 재생성), iOS 는 AppleLanguages(다음 실행부터)
 *
 * 시작 때 저장값을 한 번 읽는다. 첫 화면 전에 값이 필요하면 [ensureLoaded] 를 기다린다.
 */
class AppSettingsRepository(
    private val storage: SettingStorage,
    scope: CoroutineScope,
) {
    private val _theme = MutableStateFlow(ThemeMode.System)
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    private val _language = MutableStateFlow(AppLanguage.System)
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private val loaded = scope.async {
        _theme.value = ThemeMode.fromStored(storage.getTheme())
        storage.getLanguage()?.let { name -> AppLanguage.entries.firstOrNull { it.name == name } }?.let { _language.value = it }
    }

    suspend fun ensureLoaded() {
        loaded.await()
    }

    suspend fun setTheme(mode: ThemeMode) {
        ensureLoaded()
        _theme.value = mode
        storage.setTheme(mode.name)
    }

    suspend fun setLanguage(language: AppLanguage) {
        ensureLoaded()
        _language.value = language
        storage.setLanguage(language.name)
    }
}
