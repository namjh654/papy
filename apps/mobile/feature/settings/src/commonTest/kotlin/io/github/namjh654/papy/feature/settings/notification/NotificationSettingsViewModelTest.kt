package io.github.namjh654.papy.feature.settings.notification

import app.cash.turbine.test
import io.github.namjh654.papy.core.common.error.AppError
import io.github.namjh654.papy.core.common.error.ErrorCode
import io.github.namjh654.papy.core.data.permission.AppPermission
import io.github.namjh654.papy.core.data.permission.PermissionController
import io.github.namjh654.papy.core.data.permission.PermissionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationSettingsViewModelTest {

    private val api = FakeNotificationSettingsApi()
    private var system = PermissionStatus.Granted
    private var openedSettings = 0
    private val permissions = object : PermissionController {
        override suspend fun status(permission: AppPermission) = system
        override suspend fun request(permission: AppPermission) = system
        override fun openSettings() { openedSettings++ }
    }
    private val vm by lazy { NotificationSettingsViewModel(NotificationSettingsRepository(api), permissions, now = { 42 }) }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun loadsPreferencesAndSystemStatus() = runTest {
        system = PermissionStatus.Denied
        assertEquals(NotificationPreferences(), vm.uiState.value.preferences)
        assertFalse(vm.uiState.value.systemEnabled)
        assertFalse(vm.uiState.value.isLoading)

        system = PermissionStatus.Granted
        vm.refreshSystem() // 기기 설정에서 돌아왔다
        assertTrue(vm.uiState.value.systemEnabled)
    }

    @Test
    fun loadFailureShowsFullError() = runTest {
        api.error = AppError.Http(500, "")
        assertEquals(ErrorCode.ServerError, vm.uiState.value.error?.code)
    }

    @Test
    fun toggleSavesAndFailureReverts() = runTest {
        vm.toggle(NotificationCategory.Chat, false)
        assertEquals(false, vm.uiState.value.preferences?.chat)

        api.error = AppError.Http(500, "")
        vm.events.test {
            vm.toggle(NotificationCategory.Notice, false)
            assertIs<NotificationSettingsEvent.UpdateFailed>(awaitItem())
        }
        assertEquals(true, vm.uiState.value.preferences?.notice)
    }

    @Test
    fun marketingChangeAnnouncesDate() = runTest {
        vm.events.test {
            vm.toggle(NotificationCategory.Marketing, true)
            assertEquals(NotificationSettingsEvent.MarketingChanged(agreed = true, atMillis = api.updatedAt), awaitItem())
        }
    }

    @Test
    fun opensSystemSettings() = runTest {
        vm.openSystemSettings()
        assertEquals(1, openedSettings)
    }
}
