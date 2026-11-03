package io.github.namjh654.papy.feature.permission

import app.cash.turbine.test
import io.github.namjh654.papy.core.data.permission.AppPermission
import io.github.namjh654.papy.core.data.permission.PermissionController
import io.github.namjh654.papy.core.data.permission.PermissionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PermissionViewModelTest {

    /** 상태를 바꿔 끼울 수 있는 대역. request 는 [onRequest] 의 결과로 상태를 바꾼다 */
    private class FakeController : PermissionController {
        val statuses = AppPermission.entries.associateWith { PermissionStatus.NotRequested }.toMutableMap()
        var onRequest: (AppPermission) -> PermissionStatus = { PermissionStatus.Granted }
        val requested = mutableListOf<AppPermission>()
        var settingsOpened = 0

        override suspend fun status(permission: AppPermission) = statuses.getValue(permission)
        override suspend fun request(permission: AppPermission): PermissionStatus {
            requested += permission
            return onRequest(permission).also { statuses[permission] = it }
        }
        override fun openSettings() { settingsOpened++ }
    }

    private val controller = FakeController()
    private val viewModel by lazy { PermissionViewModel(controller) }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun refreshReadsAllStatuses() = runTest {
        assertTrue(viewModel.uiState.value.statuses.isEmpty())

        viewModel.refresh()

        assertEquals(PermissionStatus.NotRequested, viewModel.uiState.value.status(AppPermission.Camera))
        assertEquals(AppPermission.entries.size, viewModel.uiState.value.statuses.size)
    }

    @Test
    fun refreshAfterReturningFromSettingsSeesNewStatus() = runTest {
        controller.statuses[AppPermission.Camera] = PermissionStatus.PermanentlyDenied
        viewModel.refresh()
        assertEquals(PermissionStatus.PermanentlyDenied, viewModel.uiState.value.status(AppPermission.Camera))

        controller.statuses[AppPermission.Camera] = PermissionStatus.Granted // 설정에서 허용
        viewModel.refresh() // 화면이 다시 보일 때

        assertEquals(PermissionStatus.Granted, viewModel.uiState.value.status(AppPermission.Camera))
    }

    @Test
    fun requestShowsSystemDialogAndStoresResult() = runTest {
        viewModel.refresh()
        controller.onRequest = { PermissionStatus.Denied }

        viewModel.request(AppPermission.Location)

        assertEquals(listOf(AppPermission.Location), controller.requested)
        assertEquals(PermissionStatus.Denied, viewModel.uiState.value.status(AppPermission.Location))
        assertNull(viewModel.uiState.value.requesting)
    }

    @Test
    fun permanentlyDeniedAsksToOpenSettingsInsteadOfRequesting() = runTest {
        controller.statuses[AppPermission.Notifications] = PermissionStatus.PermanentlyDenied
        viewModel.refresh()

        viewModel.events.test {
            viewModel.request(AppPermission.Notifications)
            assertEquals(PermissionEvent.SettingsRequired(AppPermission.Notifications), awaitItem())
        }
        assertTrue(controller.requested.isEmpty()) // 시스템 창이 뜨지 않는 상태라 요청하지 않는다

        viewModel.openSettings()
        assertEquals(1, controller.settingsOpened)
    }
}
