package cloud.elsa.app.screen.community

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cloud.elsa.app.R
import cloud.elsa.app.component.EventEffect
import cloud.elsa.app.designsystem.component.ElsaFloatingButton
import cloud.elsa.app.designsystem.component.ElsaMessageType
import cloud.elsa.app.designsystem.component.ElsaPagedList
import cloud.elsa.app.designsystem.component.ElsaPostCard
import cloud.elsa.app.designsystem.component.ElsaPostImage
import cloud.elsa.app.designsystem.component.ElsaScaffold
import cloud.elsa.app.designsystem.component.ElsaScreenHeader
import cloud.elsa.app.designsystem.component.ElsaSegmentedTabs
import cloud.elsa.app.designsystem.component.ElsaSkeletonCard
import cloud.elsa.app.designsystem.component.rememberElsaToastState
import cloud.elsa.app.designsystem.theme.ElsaTheme
import cloud.elsa.app.designsystem.token.ElsaSize
import cloud.elsa.app.designsystem.token.ElsaSpacing
import cloud.elsa.app.feature.community.CommunityEvent
import cloud.elsa.app.feature.community.CommunityFilter
import cloud.elsa.app.feature.community.CommunityViewModel
import cloud.elsa.app.feature.community.Post
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * 커뮤니티 목록(탭): 큰 제목, 글 카드(글쓴이·시간·본문 3줄·사진·좋아요·댓글), 당겨서 새로고침·끝에서 다음, 오른쪽 아래 글쓰기.
 * @param onBack 탭 루트면 null
 */
@Composable
fun CommunityScreen(
    onItemSelected: (Post) -> Unit,
    onCompose: () -> Unit,
    onAuthorSelected: (userId: String) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val viewModel: CommunityViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val toast = rememberElsaToastState()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val likeFailed = stringResource(R.string.community_like_failed)

    EventEffect(viewModel.events) { event ->
        when (event) {
            is CommunityEvent.PostSelected -> onItemSelected(event.post)
            CommunityEvent.ComposeRequested -> onCompose()
            CommunityEvent.LikeFailed -> scope.launch { toast.show(likeFailed, ElsaMessageType.Error) }
            CommunityEvent.ScrollToTop -> scope.launch { listState.animateScrollToItem(0) }
        }
    }

    ElsaScaffold(onBack = onBack, toastState = toast, containerColor = ElsaTheme.colors.backgroundGrouped) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            ElsaPagedList(
                state = state.list,
                key = { it.id },
                emptyTitle = stringResource(R.string.community_empty),
                onRefresh = viewModel::refresh,
                onLoadMore = viewModel::loadMore,
                onRetry = viewModel::load,
                onRetryLoadMore = viewModel::retryLoadMore,
                listState = listState,
                // 글쓰기 버튼에 마지막 카드가 가리지 않게
                contentPadding = PaddingValues(start = ElsaSpacing.lg, end = ElsaSpacing.lg, top = ElsaSize.screenPadding, bottom = ElsaSize.controlHeight * 2),
                header = {
                    item(key = "header") {
                        ElsaScreenHeader(title = stringResource(R.string.community_title), modifier = Modifier.padding(horizontal = ElsaSpacing.sm))
                    }
                    item(key = "filter") {
                        ElsaSegmentedTabs(
                            options = listOf(stringResource(R.string.community_filter_all), stringResource(R.string.community_filter_mine)),
                            selectedIndex = state.filter.ordinal,
                            onSelect = { viewModel.filterChanged(CommunityFilter.entries[it]) },
                        )
                    }
                },
                loadingContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(ElsaSpacing.md)) { repeat(3) { ElsaSkeletonCard() } }
                },
            ) { post ->
                CommunityPostCard(
                    post = post,
                    onClick = { viewModel.postClicked(post) },
                    onLike = { viewModel.likeClicked(post) },
                    onAuthorClick = { onAuthorSelected(post.author.id) },
                )
            }
            ElsaFloatingButton(
                icon = R.drawable.elsa_ic_edit,
                contentDescription = stringResource(R.string.community_write),
                onClick = viewModel::composeClicked,
                modifier = Modifier.align(Alignment.BottomEnd).padding(ElsaSize.screenPadding),
            )
        }
    }
}

/**
 * 목록의 글 카드(커뮤니티 목록·사용자 프로필이 같이 쓴다). 메뉴(수정·삭제·신고·차단)는 상세에서 — 목록은 가볍게.
 * @param onAuthorClick null 이면 글쓴이를 누를 수 없다(이미 그 사람의 프로필)
 */
@Composable
fun CommunityPostCard(post: Post, onClick: () -> Unit, onLike: () -> Unit, onAuthorClick: (() -> Unit)?) {
    ElsaPostCard(
        authorName = post.author.name,
        avatarUrl = post.author.avatarUrl,
        time = postTime(post.createdAt),
        content = post.content,
        imageCount = post.imageUrls.size,
        liked = post.likedByMe,
        likeCount = post.likeCount,
        commentCount = post.commentCount,
        onClick = onClick,
        onLike = onLike,
        onMore = null,
        onAuthorClick = onAuthorClick,
        image = { index, modifier -> ElsaPostImage(url = post.imageUrls[index], modifier = modifier) },
    )
}
