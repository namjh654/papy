package io.github.namjh654.papy.core.data.storage.setting

import io.github.namjh654.papy.core.data.storage.LocalStorage
import io.github.namjh654.papy.core.data.storage.getBoolean
import io.github.namjh654.papy.core.data.storage.getInt
import io.github.namjh654.papy.core.data.storage.getLong
import io.github.namjh654.papy.core.data.storage.setBoolean
import io.github.namjh654.papy.core.data.storage.setInt
import io.github.namjh654.papy.core.data.storage.setLong

class SettingStorage(
    private val localStorage: LocalStorage
) {

    suspend fun setTheme(theme: String) {
        localStorage.setString(SettingStorageKey.THEME, theme)
    }

    suspend fun getTheme(): String? {
        return localStorage.getString(SettingStorageKey.THEME)
    }

    suspend fun setLanguage(language: String) {
        localStorage.setString(SettingStorageKey.LANGUAGE, language)
    }

    suspend fun getLanguage(): String? {
        return localStorage.getString(SettingStorageKey.LANGUAGE)
    }

    suspend fun setPush(enable: Boolean) {
        localStorage.setBoolean(SettingStorageKey.PUSH, enable)
    }

    suspend fun isPushEnabled(): Boolean {
        return localStorage.getBoolean(SettingStorageKey.PUSH) ?: false
    }

    suspend fun setAppVersion(version: Int) {
        localStorage.setInt(SettingStorageKey.APP_VERSION, version)
    }

    suspend fun getAppVersion(): Int? {
        return localStorage.getInt(SettingStorageKey.APP_VERSION)
    }

    suspend fun setLastSync(time: Long) {
        localStorage.setLong(SettingStorageKey.LAST_SYNC, time)
    }

    suspend fun getLastSync(): Long? {
        return localStorage.getLong(SettingStorageKey.LAST_SYNC)
    }

    /** 온보딩을 봤는지(첫 실행 판별). 로그아웃·탈퇴해도 지우지 않는다 — [clear] 에 넣지 않는다 */
    suspend fun setOnboardingDone() {
        localStorage.setBoolean(SettingStorageKey.ONBOARDING_DONE, true)
    }

    suspend fun isOnboardingDone(): Boolean {
        return localStorage.getBoolean(SettingStorageKey.ONBOARDING_DONE) ?: false
    }

    suspend fun clear() {
        localStorage.remove(SettingStorageKey.THEME)
        localStorage.remove(SettingStorageKey.LANGUAGE)
        localStorage.remove(SettingStorageKey.PUSH)
        localStorage.remove(SettingStorageKey.APP_VERSION)
        localStorage.remove(SettingStorageKey.LAST_SYNC)
    }
}
