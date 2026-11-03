package io.github.namjh654.papy.feature.notice.detail

import io.github.namjh654.papy.core.common.viewmodel.UiState
import io.github.namjh654.papy.core.common.error.UiError
import io.github.namjh654.papy.feature.notice.NoticeItem

/** 읽기 전용 상세: [item] 이 없으면 전체 로딩 또는 전체 오류(다시 시도) */
data class NoticeDetailUiState(
    val item: NoticeItem? = null,
    val isLoading: Boolean = true,
    val error: UiError? = null,
) : UiState
