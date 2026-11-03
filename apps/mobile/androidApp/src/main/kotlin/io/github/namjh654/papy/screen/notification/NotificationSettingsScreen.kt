package io.github.namjh654.papy.screen.notification

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
import io.github.namjh654.papy.R
import io.github.namjh654.papy.component.EventEffect
import io.github.namjh654.papy.component.message
import io.github.namjh654.papy.designsystem.component.PapyButton
import io.github.namjh654.papy.designsystem.component.PapyButtonSize
import io.github.namjh654.papy.designsystem.component.PapyButtonStyle
import io.github.namjh654.papy.designsystem.component.PapyErrorState
import io.github.namjh654.papy.designsystem.component.PapyInlineMessage
import io.github.namjh654.papy.designsystem.component.PapyLoadingIndicator
import io.github.namjh654.papy.designsystem.component.PapyMessageType
import io.github.namjh654.papy.designsystem.component.PapyScaffold
import io.github.namjh654.papy.designsystem.component.PapyScreenHeader
import io.github.namjh654.papy.designsystem.component.PapySettingsDivider
import io.github.namjh654.papy.designsystem.component.PapySettingsSection
import io.github.namjh654.papy.designsystem.component.PapySettingsSwitchRow
import io.github.namjh654.papy.designsystem.component.rememberPapyToastState
import io.github.namjh654.papy.designsystem.format.PapyFormat
import io.github.namjh654.papy.designsystem.format.currentLocale
import io.github.namjh654.papy.designsystem.theme.PapyTheme
import io.github.namjh654.papy.designsystem.token.PapySize
import io.github.namjh654.papy.designsystem.token.PapySpacing
import io.github.namjh654.papy.feature.settings.notification.NotificationCategory
import io.github.namjh654.papy.feature.settings.notification.NotificationPreferences
import io.github.namjh654.papy.feature.settings.notification.NotificationSettingsEvent
import io.github.namjh654.papy.feature.settings.notification.NotificationSettingsViewModel
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
    val toast = rememberPapyToastState()
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
                val date = PapyFormat.date(event.atMillis, locale)
                val text = context.getString(if (event.agreed) R.string.notification_marketing_agreed else R.string.notification_marketing_withdrawn, date)
                scope.launch { toast.show(text, PapyMessageType.Success) }
            }
            is NotificationSettingsEvent.UpdateFailed -> scope.launch { toast.show(event.error.message(context), PapyMessageType.Error) }
        }
    }

    PapyScaffold(onBack = onBack, toastState = toast, containerColor = PapyTheme.colors.backgroundGrouped) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            val preferences = state.preferences
            when {
                preferences == null && state.error != null ->
                    PapyErrorState(message = state.error!!.message(), onRetry = viewModel::load, modifier = Modifier.align(Alignment.Center))
                preferences == null -> PapyLoadingIndicator(modifier = Modifier.align(Alignment.Center))
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
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(PapySize.screenPadding),
        verticalArrangement = Arrangement.spacedBy(PapySpacing.xl),
    ) {
        PapyScreenHeader(title = stringResource(R.string.notification_settings_title), description = stringResource(R.string.notification_settings_description))
        if (!systemEnabled) {
            Column(verticalArrangement = Arrangement.spacedBy(PapySpacing.sm)) {
                PapyInlineMessage(message = stringResource(R.string.notification_system_off), type = PapyMessageType.Warning)
                PapyButton(
                    text = stringResource(R.string.notification_open_settings),
                    onClick = onOpenSystemSettings,
                    style = PapyButtonStyle.Secondary,
                    size = PapyButtonSize.Small,
                    fullWidth = false,
                )
            }
        }
        PapySettingsSection(title = stringResource(R.string.notification_section_activity)) {
            PapySettingsSwitchRow(
                title = stringResource(R.string.notification_chat),
                description = stringResource(R.string.notification_chat_description),
                checked = preferences.chat,
                onCheckedChange = { onToggle(NotificationCategory.Chat, it) },
            )
            PapySettingsDivider()
            PapySettingsSwitchRow(
                title = stringResource(R.string.notification_comment),
                description = stringResource(R.string.notification_comment_description),
                checked = preferences.comment,
                onCheckedChange = { onToggle(NotificationCategory.Comment, it) },
            )
        }
        PapySettingsSection(title = stringResource(R.string.notification_section_news)) {
            PapySettingsSwitchRow(
                title = stringResource(R.string.notification_notice),
                description = stringResource(R.string.notification_notice_description),
                checked = preferences.notice,
                onCheckedChange = { onToggle(NotificationCategory.Notice, it) },
            )
            PapySettingsDivider()
            PapySettingsSwitchRow(
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
private fun NotificationSettingsPreview() = PapyTheme {
    Column(modifier = Modifier.background(PapyTheme.colors.backgroundGrouped)) {
        NotificationSettingsContent(NotificationPreferences(comment = false), systemEnabled = false, onToggle = { _, _ -> }, onOpenSystemSettings = {})
    }
}
