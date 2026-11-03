package cloud.elsa.app.feature.community.user

import cloud.elsa.app.core.common.error.toAppError
import cloud.elsa.app.core.common.paging.Paginator
import cloud.elsa.app.core.common.viewmodel.StatefulViewModel
import cloud.elsa.app.feature.community.CommunityRepository
import cloud.elsa.app.feature.community.Post
import cloud.elsa.app.feature.community.PostChange
import cloud.elsa.app.feature.community.withLike
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 사용자 프로필(25-1): 글쓴이 이름·사진을 누르면 열린다. 프로필 + 그 사람이 쓴 글(페이징), 메시지 보내기, 차단.
 * 다른 화면의 변경(수정·삭제)은 다시 불러오지 않고 반영한다.
 * Swift: `IOSAppGraph.shared.userProfileViewModel(userId:)`
 */
class UserProfileViewModel(
    private val userId: String,
    private val repository: CommunityRepository,
) : StatefulViewModel<UserProfileUiState, UserProfileEvent>(UserProfileUiState()) {

    val uiState: StateFlow<UserProfileUiState> = state
    val events: Flow<UserProfileEvent> = eventFlow

    private val paginator = Paginator { page -> repository.userPosts(userId, page) }

    init {
        launch { paginator.state.collect { list -> setState { copy(posts = list) } } }
        launch { repository.changes.collect(::onChange) }
        load()
    }

    /** 처음 불러오기(전체 오류 화면의 다시 시도도 이것) */
    fun load() = launch {
        setState { copy(isLoading = true, error = null) }
        val profile = repository.user(userId)
        setState { copy(profile = profile, isLoading = false) }
        paginator.load()
    }

    fun refresh() = launch {
        runCatching { repository.user(userId) }.getOrNull()?.let { setState { copy(profile = it) } }
        paginator.refresh()
    }

    fun loadMore() = launch { paginator.loadMore() }

    fun retryLoadMore() = launch { paginator.retryLoadMore() }

    fun postClicked(post: Post) = launch { sendEvent(UserProfileEvent.PostSelected(post)) }

    fun messageClicked() = launch {
        val profile = currentState.profile?.takeIf { !it.isMe } ?: return@launch
        sendEvent(UserProfileEvent.MessageRequested(profile.id, profile.name))
    }

    /** 화면의 확인 다이얼로그 뒤에 부른다 */
    fun block() = action {
        if (!currentState.canInteract) return@action
        repository.block(userId)
        sendEvent(UserProfileEvent.Blocked)
    }

    /** 좋아요는 목록과 같이 낙관적으로 바꾸고 실패하면 되돌린다 */
    fun likeClicked(post: Post) = action {
        val liked = !post.likedByMe
        paginator.update { items -> items.map { if (it.id == post.id) it.withLike(liked) else it } }
        try {
            val result = repository.setLiked(post.id, liked)
            paginator.update { items -> items.map { if (it.id == post.id) it.copy(likeCount = result.likeCount, likedByMe = result.likedByMe) else it } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            paginator.update { items -> items.map { if (it.id == post.id) post else it } }
            throw e
        }
    }

    private fun onChange(change: PostChange) {
        when (change) {
            is PostChange.Saved -> paginator.update { items -> items.map { if (it.id == change.post.id) change.post else it } }
            is PostChange.Deleted -> {
                paginator.update { items -> items.filterNot { it.id == change.postId } }
                setState { copy(profile = profile?.let { it.copy(postCount = (it.postCount - 1).coerceAtLeast(0)) }) }
            }
            is PostChange.AuthorBlocked -> Unit
        }
    }

    /** 동작 실패는 화면 전체 오류가 아니라 안내로 */
    private fun action(block: suspend () -> Unit) = launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            sendEvent(UserProfileEvent.ActionFailed(e.toAppError().toUiError()))
        }
    }

    /** 프로필 불러오기 실패만 화면 전체 오류로 */
    override fun onError(throwable: Throwable) = setState {
        copy(isLoading = false, error = throwable.toAppError().toUiError())
    }
}
