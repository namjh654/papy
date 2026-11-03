package io.github.namjh654.papy.feature.settings.notification

import io.github.namjh654.papy.core.common.error.toAppError
import io.github.namjh654.papy.core.common.viewmodel.StatefulViewModel
import io.github.namjh654.papy.core.data.permission.AppPermission
import io.github.namjh654.papy.core.data.permission.PermissionController
import io.github.namjh654.papy.core.data.permission.PermissionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Clock

/**
 * 알림 설정(26-2): 서버의 종류별 받기 값을 바로 바꾸고(낙관적) 실패하면 되돌린다. 광고성 알림은 바꾼 날짜를 안내한다.
 * 기기 알림 권한이 꺼져 있으면 위에 안내 — 화면이 다시 보일 때 [refreshSystem].
 * Swift: `IOSAppGraph.shared.notificationSettingsViewModel()`
 */
class NotificationSettingsViewModel(
    private val repository: NotificationSettingsRepository,
    private val permissions: PermissionController,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : StatefulViewModel<NotificationSettingsUiState, NotificationSettingsEvent>(NotificationSettingsUiState()) {

    val uiState: StateFlow<NotificationSettingsUiState> = state
    val events: Flow<NotificationSettingsEvent> = eventFlow

    init {
        load()
    }

    fun load() = launch {
        setState { copy(isLoading = true, error = null) }
        refreshSystemStatus()
        val preferences = repository.load()
        setState { copy(preferences = preferences, isLoading = false) }
    }

    /** 기기 설정에서 돌아왔을 때 */
    fun refreshSystem() = launch { refreshSystemStatus() }

    fun openSystemSettings() = permissions.openSettings()

    fun toggle(category: NotificationCategory, enabled: Boolean) = launch {
        val before = currentState.preferences ?: return@launch
        setState { copy(preferences = before.with(category, enabled)) }
        try {
            val saved = repository.set(before, category, enabled)
            setState { copy(preferences = saved) }
            if (category == NotificationCategory.Marketing) {
                sendEvent(NotificationSettingsEvent.MarketingChanged(agreed = saved.marketing, atMillis = saved.marketingUpdatedAt ?: now()))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            setState { copy(preferences = before) }
            sendEvent(NotificationSettingsEvent.UpdateFailed(e.toAppError().toUiError()))
        }
    }

    private suspend fun refreshSystemStatus() {
        val status = permissions.status(AppPermission.Notifications)
        setState { copy(systemEnabled = status == PermissionStatus.Granted) }
    }

    /** 처음 불러오기 실패만 전체 오류로 */
    override fun onError(throwable: Throwable) = setState {
        copy(isLoading = false, error = throwable.toAppError().toUiError())
    }
}
