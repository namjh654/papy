package io.github.namjh654.papy.feature.permission

import io.github.namjh654.papy.core.common.viewmodel.UiEvent
import io.github.namjh654.papy.core.data.permission.AppPermission

sealed interface PermissionEvent : UiEvent {
    /** 시스템 창이 더는 뜨지 않는다 → 화면이 "설정에서 허용해 주세요" 다이얼로그를 띄운다 */
    data class SettingsRequired(val permission: AppPermission) : PermissionEvent
}
