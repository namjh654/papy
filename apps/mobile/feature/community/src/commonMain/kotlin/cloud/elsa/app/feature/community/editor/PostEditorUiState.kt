package cloud.elsa.app.feature.community.editor

import cloud.elsa.app.core.common.error.UiError
import cloud.elsa.app.core.common.viewmodel.UiState
import cloud.elsa.app.feature.community.CommunityRepository
import cloud.elsa.app.feature.community.PostImage

/**
 * 글쓰기·고치기. [images] 는 이미 올라간 주소와 새로 고른 사진이 섞여 있고 순서 그대로 저장된다.
 * Swift: `images` 는 `[PostImage]` — `onEnum(of:)` 로 `remote`·`local`
 */
data class PostEditorUiState(
    val isEditing: Boolean = false,
    val content: String = "",
    val images: List<PostImage> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: UiError? = null,
) : UiState {
    val maxImages: Int get() = CommunityRepository.MAX_IMAGES
    val maxContent: Int get() = CommunityRepository.MAX_CONTENT
    val canAddImage: Boolean get() = images.size < maxImages && !isSaving
    val canSave: Boolean get() = content.isNotBlank() && content.length <= maxContent && !isSaving && !isLoading
}
