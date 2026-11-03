package cloud.elsa.app.feature.community

import cloud.elsa.app.core.common.paging.Paginator
import cloud.elsa.app.core.common.viewmodel.StatefulViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 커뮤니티 목록: 최신순 페이징, 좋아요는 바로 바꾸고 실패하면 되돌린다(낙관적),
 * 다른 화면의 변경(새 글·수정·삭제·차단, [PostChange])은 다시 불러오지 않고 목록에 반영한다.
 * Swift: `IOSAppGraph.shared.communityViewModel()`
 */
class CommunityViewModel(
    private val repository: CommunityRepository,
) : StatefulViewModel<CommunityUiState, CommunityEvent>(CommunityUiState()) {

    val uiState: StateFlow<CommunityUiState> = state
    val events: Flow<CommunityEvent> = eventFlow

    // 저장된 전체 목록을 먼저 보여 주고 받는다(DEC-130). 시작 범위는 항상 전체
    private val paginator = Paginator(cache = repository::cachedPosts) { page -> repository.posts(page, currentState.filter) }

    init {
        launch { paginator.state.collect { list -> setState { copy(list = list) } } }
        launch { repository.changes.collect(::onChange) }
        load()
    }

    /** 첫 페이지(전체 오류 화면의 다시 시도도 이것) */
    fun load() = launch { paginator.load() }

    fun refresh() = launch { paginator.refresh() }

    fun loadMore() = launch { paginator.loadMore() }

    fun retryLoadMore() = launch { paginator.retryLoadMore() }

    /** 전체 ↔ 내 글: 처음부터 다시 불러온다(이전 요청 결과는 버린다) */
    fun filterChanged(filter: CommunityFilter) {
        if (filter == currentState.filter) return
        setState { copy(filter = filter) }
        launch { paginator.restart() }
    }

    fun postClicked(post: Post) = launch { sendEvent(CommunityEvent.PostSelected(post)) }

    fun composeClicked() = launch { sendEvent(CommunityEvent.ComposeRequested) }

    fun likeClicked(post: Post) = launch {
        val liked = !post.likedByMe
        paginator.update { items -> items.map { if (it.id == post.id) it.withLike(liked) else it } }
        try {
            val result = repository.setLiked(post.id, liked)
            paginator.update { items -> items.map { if (it.id == post.id) it.copy(likeCount = result.likeCount, likedByMe = result.likedByMe) else it } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            paginator.update { items -> items.map { if (it.id == post.id) it.copy(likeCount = post.likeCount, likedByMe = post.likedByMe) else it } }
            sendEvent(CommunityEvent.LikeFailed)
        }
    }

    private suspend fun onChange(change: PostChange) {
        when (change) {
            // 새 글은 내 글이라 두 범위 모두 맨 위에 들어간다
            is PostChange.Saved -> if (change.isNew) {
                paginator.update { items -> listOf(change.post) + items }
                sendEvent(CommunityEvent.ScrollToTop)
            } else {
                paginator.update { items -> items.map { if (it.id == change.post.id) change.post else it } }
            }
            is PostChange.Deleted -> paginator.update { items -> items.filterNot { it.id == change.postId } }
            is PostChange.AuthorBlocked -> paginator.update { items -> items.filterNot { it.author.id == change.authorId } }
        }
    }
}

/** 좋아요를 바꾼 모습(서버 응답 전) */
internal fun Post.withLike(liked: Boolean): Post =
    if (liked == likedByMe) this else copy(likedByMe = liked, likeCount = (likeCount + if (liked) 1 else -1).coerceAtLeast(0))
