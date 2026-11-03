package cloud.elsa.app.screen.notification

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cloud.elsa.app.R
import cloud.elsa.app.component.EventEffect
import cloud.elsa.app.component.message
import cloud.elsa.app.designsystem.component.ElsaButton
import cloud.elsa.app.designsystem.component.ElsaButtonSize
import cloud.elsa.app.designsystem.component.ElsaButtonStyle
import cloud.elsa.app.designsystem.component.ElsaErrorState
import cloud.elsa.app.designsystem.component.ElsaInlineMessage
import cloud.elsa.app.designsystem.component.ElsaLoadingIndicator
import cloud.elsa.app.designsystem.component.ElsaMessageType
import cloud.elsa.app.designsystem.component.ElsaScaffold
import cloud.elsa.app.designsystem.component.ElsaScreenHeader
import cloud.elsa.app.designsystem.component.ElsaSettingsDivider
import cloud.elsa.app.designsystem.component.ElsaSettingsSection
import cloud.elsa.app.designsystem.component.ElsaSettingsSwitchRow
import cloud.elsa.app.designsystem.component.rememberElsaToastState
import cloud.elsa.app.designsystem.format.ElsaFormat
import cloud.elsa.app.designsystem.format.currentLocale
import cloud.elsa.app.designsystem.theme.ElsaTheme
import cloud.elsa.app.designsystem.token.ElsaSize
import cloud.elsa.app.designsystem.token.ElsaSpacing
import cloud.elsa.app.feature.settings.notification.NotificationCategory
import cloud.elsa.app.feature.settings.notification.NotificationPreferences
import cloud.elsa.app.feature.settings.notification.NotificationSettingsEvent
import cloud.elsa.app.feature.settings.notification.NotificationSettingsViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * 설정 → 알림 설정(26-2): 기기 알림이 꺼져 있으면 위에 안내(설정 열기), 아래 종류별 스위치(활동: 채팅·댓글 / 소식: 공지·광고성).
 * 광고성 알림을 바꾸면 처리 날짜를 안내한다(법정 고지). 기기 설정에서 돌아오면 다시 확인한다. iOS `notification.settings.screen.swift`
 */
@Composable
fun NotificationSettingsScreen(onBack: () -> Unit) {
    val viewModel: NotificationSettingsViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val toast = rememberElsaToastState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val locale = currentLocale
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshSystem() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    EventEffect(viewModel.events) { event ->
        when (event) {
            is NotificationSettingsEvent.MarketingChanged -> {
                val date = ElsaFormat.date(event.atMillis, locale)
                val text = context.getString(if (event.agreed) R.string.notification_marketing_agreed else R.string.notification_marketing_withdrawn, date)
                scope.launch { toast.show(text, ElsaMessageType.Success) }
            }
            is NotificationSettingsEvent.UpdateFailed -> scope.launch { toast.show(event.error.message(context), ElsaMessageType.Error) }
        }
    }

    ElsaScaffold(onBack = onBack, toastState = toast, containerColor = ElsaTheme.colors.backgroundGrouped) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            val preferences = state.preferences
            when {
                preferences == null && state.error != null ->
                    ElsaErrorState(message = state.error!!.message(), onRetry = viewModel::load, modifier = Modifier.align(Alignment.Center))
                preferences == null -> ElsaLoadingIndicator(modifier = Modifier.align(Alignment.Center))
                else -> NotificationSettingsContent(
                    preferences = preferences,
                    systemEnabled = state.systemEnabled,
                    onToggle = viewModel::toggle,
                    onOpenSystemSettings = viewModel::openSystemSettings,
                )
            }
        }
    }
}

/** 본문(미리보기 대상) */
@Composable
fun NotificationSettingsContent(
    preferences: NotificationPreferences,
    systemEnabled: Boolean,
    onToggle: (NotificationCategory, Boolean) -> Unit,
    onOpenSystemSettings: () -> Unit,
) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(ElsaSize.screenPadding),
        verticalArrangement = Arrangement.spacedBy(ElsaSpacing.xl),
    ) {
        ElsaScreenHeader(title = stringResource(R.string.notification_settings_title), description = stringResource(R.string.notification_settings_description))
        if (!systemEnabled) {
            Column(verticalArrangement = Arrangement.spacedBy(ElsaSpacing.sm)) {
                ElsaInlineMessage(message = stringResource(R.string.notification_system_off), type = ElsaMessageType.Warning)
                ElsaButton(
                    text = stringResource(R.string.notification_open_settings),
                    onClick = onOpenSystemSettings,
                    style = ElsaButtonStyle.Secondary,
                    size = ElsaButtonSize.Small,
                    fullWidth = false,
                )
            }
        }
        ElsaSettingsSection(title = stringResource(R.string.notification_section_activity)) {
            ElsaSettingsSwitchRow(
                title = stringResource(R.string.notification_chat),
                description = stringResource(R.string.notification_chat_description),
                checked = preferences.chat,
                onCheckedChange = { onToggle(NotificationCategory.Chat, it) },
            )
            ElsaSettingsDivider()
            ElsaSettingsSwitchRow(
                title = stringResource(R.string.notification_comment),
                description = stringResource(R.string.notification_comment_description),
                checked = preferences.comment,
                onCheckedChange = { onToggle(NotificationCategory.Comment, it) },
            )
        }
        ElsaSettingsSection(title = stringResource(R.string.notification_section_news)) {
            ElsaSettingsSwitchRow(
                title = stringResource(R.string.notification_notice),
                description = stringResource(R.string.notification_notice_description),
                checked = preferences.notice,
                onCheckedChange = { onToggle(NotificationCategory.Notice, it) },
            )
            ElsaSettingsDivider()
            ElsaSettingsSwitchRow(
                title = stringResource(R.string.notification_marketing),
                description = stringResource(R.string.notification_marketing_description),
                checked = preferences.marketing,
                onCheckedChange = { onToggle(NotificationCategory.Marketing, it) },
            )
        }
    }
}

@Preview(name = "라이트")
@Preview(name = "다크", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NotificationSettingsPreview() = ElsaTheme {
    Column(modifier = Modifier.background(ElsaTheme.colors.backgroundGrouped)) {
        NotificationSettingsContent(NotificationPreferences(comment = false), systemEnabled = false, onToggle = { _, _ -> }, onOpenSystemSettings = {})
    }
}
