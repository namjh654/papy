package cloud.elsa.app.feature.community.editor

import cloud.elsa.app.core.common.error.toAppError
import cloud.elsa.app.core.common.viewmodel.StatefulViewModel
import cloud.elsa.app.core.data.media.AttachmentInput
import cloud.elsa.app.feature.community.CommunityRepository
import cloud.elsa.app.feature.community.PostImage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlin.uuid.Uuid

/**
 * 글쓰기(20-1): 내용(최대 2000자)·사진(최대 4장). [postId] 가 있으면 그 글을 불러와 고친다(내 글).
 * 사진 미리보기는 플랫폼이 [addImage] 가 돌려주는 key 로 들고 있는다(메시지 첨부와 같은 방식).
 * Swift: `IOSAppGraph.shared.postEditorViewModel(postId:)` — 새 글은 nil
 */
class PostEditorViewModel(
    private val postId: String?,
    private val repository: CommunityRepository,
    private val newKey: () -> String = { Uuid.random().toString() },
) : StatefulViewModel<PostEditorUiState, PostEditorEvent>(PostEditorUiState(isEditing = postId != null)) {

    val uiState: StateFlow<PostEditorUiState> = state
    val events: Flow<PostEditorEvent> = eventFlow

    init {
        if (postId != null) {
            launch {
                setState { copy(isLoading = true) }
                val post = repository.post(postId)
                setState { copy(content = post.content, images = post.imageUrls.map { PostImage.Remote(it) }, isLoading = false) }
            }
        }
    }

    fun contentChanged(value: String) = setState { copy(content = value, error = null) }

    /** @return 미리보기 key, 더 넣을 수 없으면 null */
    fun addImage(input: AttachmentInput): String? {
        if (!currentState.canAddImage) return null
        val key = newKey()
        setState { copy(images = images + PostImage.Local(key, input), error = null) }
        return key
    }

    fun removeImage(index: Int) = setState { copy(images = images.filterIndexed { i, _ -> i != index }) }

    fun save() = launch {
        if (!currentState.canSave) return@launch
        setState { copy(isSaving = true, error = null) }
        val post = repository.save(postId, currentState.content, currentState.images)
        setState { copy(isSaving = false) }
        sendEvent(PostEditorEvent.Saved(post))
    }

    override fun onError(throwable: Throwable) = setState {
        copy(isSaving = false, isLoading = false, error = throwable.toAppError().toUiError())
    }
}
