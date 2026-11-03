package io.github.namjh654.papy.core.data.settings

import app.cash.turbine.test
import io.github.namjh654.papy.core.data.storage.FakeLocalStorage
import io.github.namjh654.papy.core.data.storage.setting.SettingStorage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AppSettingsRepositoryTest {

    @Test
    fun loadsSavedValuesAndPersistsChanges() = runTest {
        val local = FakeLocalStorage()
        SettingStorage(local).apply {
            setTheme("Dark")
            setLanguage("English")
        }
        val repository = AppSettingsRepository(SettingStorage(local), backgroundScope)
        repository.ensureLoaded()
        assertEquals(ThemeMode.Dark, repository.theme.value)
        assertEquals(AppLanguage.English, repository.language.value)

        repository.theme.test {
            assertEquals(ThemeMode.Dark, awaitItem())
            repository.setTheme(ThemeMode.Light)
            assertEquals(ThemeMode.Light, awaitItem())
        }
        assertEquals("Light", SettingStorage(local).getTheme())
    }

    @Test
    fun unknownSavedValueFallsBackToSystem() = runTest {
        val local = FakeLocalStorage().apply { values["setting.theme"] = "Sepia" }
        val repository = AppSettingsRepository(SettingStorage(local), backgroundScope)
        repository.ensureLoaded()

        assertEquals(ThemeMode.System, repository.theme.value)
        assertEquals(AppLanguage.System, repository.language.value)
    }

    @Test
    fun noSavedThemeFollowsSystem() = runTest {
        val repository = AppSettingsRepository(SettingStorage(FakeLocalStorage()), backgroundScope)
        repository.ensureLoaded()
        assertEquals(ThemeMode.System, repository.theme.value)
    }
}

/** 테마 결정 규칙 (재발 방지: 시스템 라이트인데 앱이 다크로 뜨지 않게) */
class ThemeModeTest {

    @Test
    fun decidesDarkForEverySystemAndSetting() {
        // (시스템 다크 여부, 설정) → 다크로 그릴지
        val expected = mapOf(
            (false to ThemeMode.System) to false,
            (true to ThemeMode.System) to true,
            (false to ThemeMode.Light) to false,
            (true to ThemeMode.Light) to false,
            (false to ThemeMode.Dark) to true,
            (true to ThemeMode.Dark) to true,
        )
        expected.forEach { (input, dark) ->
            assertEquals(dark, input.second.isDark(systemDark = input.first), "system dark=${input.first}, ${input.second}")
        }
    }

    @Test
    fun onlySystemFollowsTheSystem() {
        assertEquals(null, ThemeMode.System.forcedDark)
        assertEquals(false, ThemeMode.Light.forcedDark)
        assertEquals(true, ThemeMode.Dark.forcedDark)
    }

    @Test
    fun storedValueParsing() {
        assertEquals(ThemeMode.System, ThemeMode.fromStored(null))
        assertEquals(ThemeMode.System, ThemeMode.fromStored(""))
        assertEquals(ThemeMode.System, ThemeMode.fromStored("sepia"))
        assertEquals(ThemeMode.Dark, ThemeMode.fromStored("Dark"))
        assertEquals(ThemeMode.Light, ThemeMode.fromStored("light")) // 대소문자 무시
    }
}
