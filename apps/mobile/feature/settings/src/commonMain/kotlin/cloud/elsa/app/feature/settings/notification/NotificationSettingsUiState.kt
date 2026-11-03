package cloud.elsa.app.feature.settings.notification

import cloud.elsa.app.core.common.error.UiError
import cloud.elsa.app.core.common.viewmodel.UiState

/**
 * 설정 → 알림 설정(26-2): 기기 알림 권한 안내 + 종류별 스위치 4개.
 * [systemEnabled] 가 false 면 위에 "기기 알림이 꺼져 있어요 — 설정 열기"(종류별 스위치는 그대로 둔다: 서버 쪽 값).
 * [error]: 처음 불러오기 실패(전체 오류). 바꾸기 실패는 되돌리고 이벤트로 알린다.
 */
data class NotificationSettingsUiState(
    val preferences: NotificationPreferences? = null,
    val systemEnabled: Boolean = true,
    val isLoading: Boolean = true,
    val error: UiError? = null,
) : UiState
