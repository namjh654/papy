package io.github.namjh654.papy.feature.home

import io.github.namjh654.papy.core.common.viewmodel.UiEvent

/** 홈 화면 일회성 이벤트. 지금은 없다(로그아웃은 설정 탭 — feature:settings). 화면 이동 알림이 생기면 추가한다 */
sealed interface HomeEvent : UiEvent
