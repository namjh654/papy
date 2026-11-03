package io.github.namjh654.papy.feature.settings.profile

import io.github.namjh654.papy.core.common.error.UiError
import io.github.namjh654.papy.core.common.viewmodel.UiState

/**
 * @property avatarUrl 저장된 사진 주소
 * @property hasNewAvatar 새 사진을 골랐다(미리보기는 화면이 갖고 있다 — 이미지 타입은 플랫폼 것이라 상태에 넣지 않는다)
 * @property isDirty 저장하지 않은 변경이 있다 → 저장 버튼 활성, 나갈 때 확인
 */
data class ProfileUiState(
    val name: String = "",
    val email: String = "",
    val avatarUrl: String? = null,
    val hasNewAvatar: Boolean = false,
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    val error: UiError? = null,
) : UiState {
    val canSave: Boolean
        get() = isDirty && name.isNotBlank() && !isSaving
}
