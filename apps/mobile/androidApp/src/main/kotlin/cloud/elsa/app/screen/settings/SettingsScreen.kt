package cloud.elsa.app.screen.settings

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cloud.elsa.app.BuildConfig
import cloud.elsa.app.R
import cloud.elsa.app.component.EventEffect
import cloud.elsa.app.core.data.settings.AppLanguage
import cloud.elsa.app.core.data.settings.ThemeMode
import cloud.elsa.app.designsystem.component.ElsaAlert
import cloud.elsa.app.designsystem.component.ElsaAlertDialog
import cloud.elsa.app.designsystem.component.ElsaAvatar
import cloud.elsa.app.designsystem.component.ElsaCard
import cloud.elsa.app.designsystem.component.ElsaOptionSheet
import cloud.elsa.app.designsystem.component.ElsaScreenHeader
import cloud.elsa.app.designsystem.component.ElsaScrollScreen
import cloud.elsa.app.designsystem.component.ElsaSettingsDivider
import cloud.elsa.app.designsystem.component.ElsaSettingsSwitchRow
import cloud.elsa.app.security.DeviceAuth
import androidx.fragment.app.FragmentActivity
import cloud.elsa.app.designsystem.component.ElsaSettingsRow
import cloud.elsa.app.designsystem.component.ElsaSettingsSection
import cloud.elsa.app.designsystem.theme.ElsaTheme
import cloud.elsa.app.designsystem.token.ElsaSpacing
import cloud.elsa.app.feature.settings.SettingsEvent
import cloud.elsa.app.feature.settings.SettingsViewModel
import org.koin.androidx.compose.koinViewModel

/**
 * 설정 탭: 화면(테마·언어) · 정보(버전·환경·약관) · 계정(로그아웃).
 * 새 행은 ElsaSettingsRow 한 줄 + 값은 KMP AppSettingsRepository / SettingsUiState.
 */
@Composable
fun SettingsScreen(
    onLoggedOut: () -> Unit,
    onOpenWeb: (url: String, title: String) -> Unit,
    onProfileClick: () -> Unit,
    onWithdrawClick: () -> Unit,
    onBlockedUsersClick: () -> Unit,
    onInquiryClick: () -> Unit,
    onNotificationSettingsClick: () -> Unit,
    onDownloadsClick: () -> Unit,
) {
    val viewModel: SettingsViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? Activity

    EventEffect(viewModel.events) { event ->
        when (event) {
            SettingsEvent.LoggedOut -> onLoggedOut()
            // MainActivity.attachBaseContext 가 저장된 언어로 다시 만든다
            is SettingsEvent.LanguageChanged -> activity?.recreate()
        }
    }

    var sheet by remember { mutableStateOf<SettingsSheet?>(null) }
    var alert by remember { mutableStateOf<ElsaAlert?>(null) }

    val logoutTitle = stringResource(R.string.settings_logout_confirm)
    val logoutText = stringResource(R.string.settings_logout)
    val cancelText = stringResource(R.string.common_cancel)
    val termsTitle = stringResource(R.string.settings_terms)
    val privacyTitle = stringResource(R.string.settings_privacy)

    ElsaScrollScreen(verticalArrangement = Arrangement.spacedBy(ElsaSpacing.xl)) {
        ElsaScreenHeader(title = stringResource(R.string.settings_title))

        state.user?.let { user ->
            ElsaCard(onClick = onProfileClick) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ElsaSpacing.lg),
                ) {
                    ElsaAvatar(name = user.name, url = user.avatarUrl)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ElsaSpacing.xxs)) {
                        Text(text = user.name, style = ElsaTheme.typography.subtitle, color = ElsaTheme.colors.onSurface)
                        Text(
                            text = user.email ?: stringResource(R.string.settings_profile_edit),
                            style = ElsaTheme.typography.bodySmall,
                            color = ElsaTheme.colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        ElsaSettingsSection(title = stringResource(R.string.settings_section_display)) {
            ElsaSettingsRow(
                title = stringResource(R.string.settings_theme),
                value = state.theme.label(),
                onClick = { sheet = SettingsSheet.Theme },
            )
            ElsaSettingsDivider()
            ElsaSettingsRow(
                title = stringResource(R.string.settings_language),
                value = state.language.label(),
                onClick = { sheet = SettingsSheet.Language },
            )
        }

        // 알림·보안(26): 알림 종류별 설정, 앱 잠금(기기에 잠금 수단이 있을 때만)
        val lockAvailable = remember { activity?.let(DeviceAuth::isAvailable) ?: false }
        val lockPrompt = stringResource(R.string.app_lock_prompt)
        ElsaSettingsSection(title = stringResource(R.string.settings_section_security)) {
            ElsaSettingsRow(title = stringResource(R.string.settings_notifications), onClick = onNotificationSettingsClick)
            if (lockAvailable) {
                ElsaSettingsDivider()
                ElsaSettingsSwitchRow(
                    title = stringResource(R.string.settings_app_lock),
                    description = stringResource(R.string.settings_app_lock_description),
                    checked = state.appLockEnabled,
                    onCheckedChange = { enabled ->
                        // 켤 때는 먼저 본인 확인 — 성공해야 켠다
                        val fragmentActivity = activity as? FragmentActivity
                        if (enabled && fragmentActivity != null) {
                            DeviceAuth.authenticate(fragmentActivity, lockPrompt, onSuccess = { viewModel.appLockChanged(true) })
                        } else {
                            viewModel.appLockChanged(false)
                        }
                    },
                )
            }
        }

        ElsaSettingsSection(title = stringResource(R.string.settings_section_info)) {
            ElsaSettingsRow(title = stringResource(R.string.settings_version), value = BuildConfig.VERSION_NAME)
            ElsaSettingsDivider()
            ElsaSettingsRow(title = stringResource(R.string.settings_rate_app), onClick = viewModel::rateApp)
            ElsaSettingsDivider()
            // 앱 안의 연락 수단(App Store 1.2, 25-3)
            ElsaSettingsRow(title = stringResource(R.string.settings_inquiry), onClick = onInquiryClick)
            state.environment?.let { environment ->
                ElsaSettingsDivider()
                ElsaSettingsRow(title = stringResource(R.string.settings_environment), value = environment)
            }
            state.termsUrl?.let { url ->
                ElsaSettingsDivider()
                ElsaSettingsRow(title = termsTitle, onClick = { onOpenWeb(url, termsTitle) })
            }
            state.privacyUrl?.let { url ->
                ElsaSettingsDivider()
                ElsaSettingsRow(title = privacyTitle, onClick = { onOpenWeb(url, privacyTitle) })
            }
        }

        ElsaSettingsSection(title = stringResource(R.string.settings_section_account)) {
            // 받아 둔 파일(30-1)
            ElsaSettingsRow(title = stringResource(R.string.settings_downloads), onClick = onDownloadsClick)
            ElsaSettingsDivider()
            // 커뮤니티 차단 해제(App Store 1.2) — 커뮤니티 모듈을 빼면 이 줄도 뺀다
            ElsaSettingsRow(title = stringResource(R.string.settings_blocked_users), onClick = onBlockedUsersClick)
            ElsaSettingsDivider()
            ElsaSettingsRow(
                title = logoutText,
                destructive = true,
                onClick = {
                    if (!state.isLoggingOut) {
                        alert = ElsaAlert(
                            title = logoutTitle,
                            confirmText = logoutText,
                            dismissText = cancelText,
                            isDestructive = true,
                            onConfirm = viewModel::logout,
                        )
                    }
                },
            )
            ElsaSettingsDivider()
            ElsaSettingsRow(title = stringResource(R.string.settings_withdraw), onClick = onWithdrawClick)
        }
    }

    when (sheet) {
        SettingsSheet.Theme -> ElsaOptionSheet(
            title = stringResource(R.string.settings_theme),
            options = ThemeMode.entries,
            selected = state.theme,
            label = { it.label() },
            onSelect = viewModel::selectTheme,
            onDismiss = { sheet = null },
        )
        SettingsSheet.Language -> ElsaOptionSheet(
            title = stringResource(R.string.settings_language),
            options = AppLanguage.entries,
            selected = state.language,
            label = { it.label() },
            onSelect = viewModel::selectLanguage,
            onDismiss = { sheet = null },
        )
        null -> Unit
    }

    ElsaAlertDialog(alert = alert, onDismiss = { alert = null })
}

private enum class SettingsSheet { Theme, Language }

@Composable
private fun ThemeMode.label(): String = stringResource(
    when (this) {
        ThemeMode.System -> R.string.settings_theme_system
        ThemeMode.Light -> R.string.settings_theme_light
        ThemeMode.Dark -> R.string.settings_theme_dark
    },
)

@Composable
private fun AppLanguage.label(): String = stringResource(
    when (this) {
        AppLanguage.System -> R.string.settings_language_system
        AppLanguage.Korean -> R.string.settings_language_ko
        AppLanguage.English -> R.string.settings_language_en
    },
)
