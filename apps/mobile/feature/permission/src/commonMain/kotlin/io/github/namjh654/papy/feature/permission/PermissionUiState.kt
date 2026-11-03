package io.github.namjh654.papy.feature.permission

import io.github.namjh654.papy.core.common.viewmodel.UiState
import io.github.namjh654.papy.core.data.permission.AppPermission
import io.github.namjh654.papy.core.data.permission.PermissionStatus

/**
 * @property statuses 권한별 현재 상태. 아직 읽지 못한 항목은 없다 → 화면은 버튼을 숨긴다
 * @property requesting 시스템 권한 창이 떠 있는 항목
 */
data class PermissionUiState(
    val statuses: Map<AppPermission, PermissionStatus> = emptyMap(),
    val requesting: AppPermission? = null,
) : UiState {
    /** Swift 에서 Map 을 읽기 쉽게 */
    fun status(permission: AppPermission): PermissionStatus? = statuses[permission]
}
