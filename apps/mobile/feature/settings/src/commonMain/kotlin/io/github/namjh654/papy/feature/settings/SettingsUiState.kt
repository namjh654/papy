package io.github.namjh654.papy.feature.settings

import io.github.namjh654.papy.core.common.viewmodel.UiState
import io.github.namjh654.papy.core.data.session.UserModel
import io.github.namjh654.papy.core.data.settings.AppLanguage
import io.github.namjh654.papy.core.data.settings.ThemeMode

/**
 * 설정 화면 상태. 앱 버전은 플랫폼이 자기 번들에서 읽는다(Android BuildConfig / iOS Bundle).
 *
 * @property environment 현재 환경 이름. env 의 app.showEnvironment 가 false(prod)면 null → 행을 숨긴다
 * @property termsUrl 약관 웹뷰 주소. 비어 있으면 null → 행을 숨긴다
 */
data class SettingsUiState(
    /** 로그인 사용자(맨 위 프로필 행) */
    val user: UserModel? = null,
    val theme: ThemeMode = ThemeMode.System,
    val language: AppLanguage = AppLanguage.System,
    val environment: String? = null,
    val termsUrl: String? = null,
    val privacyUrl: String? = null,
    val isLoggingOut: Boolean = false,
    /** 앱 잠금(생체 인증, 26-1) */
    val appLockEnabled: Boolean = false,
) : UiState
