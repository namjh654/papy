package io.github.namjh654.papy.feature.permission

import io.github.namjh654.papy.core.common.viewmodel.StatefulViewModel
import io.github.namjh654.papy.core.data.permission.AppPermission
import io.github.namjh654.papy.core.data.permission.PermissionController
import io.github.namjh654.papy.core.data.permission.PermissionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 권한 요청 기준 예시. 권한이 필요한 화면은 이 흐름을 따른다:
 *
 * 1. **다시 확인**: 화면이 보일 때마다 [refresh] 를 부른다 — 사용자가 설정 앱에서 바꾸고 돌아올 수 있다
 *    (Android `LifecycleResumeEffect`, iOS `scenePhase == .active`)
 * 2. **요청**: 허용 전이면 시스템 창을 띄운다
 * 3. **설정으로 안내**: 시스템 창이 더는 뜨지 않는 상태(두 번 거부 등)면 확인 다이얼로그 뒤 설정 화면을 연다
 */
class PermissionViewModel(
    private val permissions: PermissionController,
) : StatefulViewModel<PermissionUiState, PermissionEvent>(PermissionUiState()) {

    val uiState: StateFlow<PermissionUiState> = state

    val events: Flow<PermissionEvent> = eventFlow

    /** 화면이 보일 때마다(처음 + 설정·권한 창에서 돌아올 때) */
    fun refresh() = launch {
        val statuses = AppPermission.entries.associateWith { permissions.status(it) }
        setState { copy(statuses = statuses) }
    }

    fun request(permission: AppPermission) = launch {
        if (currentState.requesting != null) return@launch // 권한 창은 한 번에 하나
        when (permissions.status(permission)) {
            PermissionStatus.Granted -> refresh()
            PermissionStatus.PermanentlyDenied -> sendEvent(PermissionEvent.SettingsRequired(permission))
            PermissionStatus.NotRequested, PermissionStatus.Denied -> {
                setState { copy(requesting = permission) }
                val result = try {
                    permissions.request(permission)
                } finally {
                    setState { copy(requesting = null) }
                }
                setState { copy(statuses = statuses + (permission to result)) }
            }
        }
    }

    /** "설정 열기" — 돌아오면 화면이 [refresh] 를 다시 부른다 */
    fun openSettings() = permissions.openSettings()
}
