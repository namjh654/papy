package io.github.namjh654.papy.screen.community

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
import io.github.namjh654.papy.R
import io.github.namjh654.papy.component.EventEffect
import io.github.namjh654.papy.designsystem.component.PapyFloatingButton
import io.github.namjh654.papy.designsystem.component.PapyMessageType
import io.github.namjh654.papy.designsystem.component.PapyPagedList
import io.github.namjh654.papy.designsystem.component.PapyPostCard
import io.github.namjh654.papy.designsystem.component.PapyPostImage
import io.github.namjh654.papy.designsystem.component.PapyScaffold
import io.github.namjh654.papy.designsystem.component.PapyScreenHeader
import io.github.namjh654.papy.designsystem.component.PapySegmentedTabs
import io.github.namjh654.papy.designsystem.component.PapySkeletonCard
import io.github.namjh654.papy.designsystem.component.rememberPapyToastState
import io.github.namjh654.papy.designsystem.theme.PapyTheme
import io.github.namjh654.papy.designsystem.token.PapySize
import io.github.namjh654.papy.designsystem.token.PapySpacing
import io.github.namjh654.papy.feature.community.CommunityEvent
import io.github.namjh654.papy.feature.community.CommunityFilter
import io.github.namjh654.papy.feature.community.CommunityViewModel
import io.github.namjh654.papy.feature.community.Post
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
    val toast = rememberPapyToastState()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val likeFailed = stringResource(R.string.community_like_failed)

    EventEffect(viewModel.events) { event ->
        when (event) {
            is CommunityEvent.PostSelected -> onItemSelected(event.post)
            CommunityEvent.ComposeRequested -> onCompose()
            CommunityEvent.LikeFailed -> scope.launch { toast.show(likeFailed, PapyMessageType.Error) }
            CommunityEvent.ScrollToTop -> scope.launch { listState.animateScrollToItem(0) }
        }
    }

    PapyScaffold(onBack = onBack, toastState = toast, containerColor = PapyTheme.colors.backgroundGrouped) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            PapyPagedList(
                state = state.list,
                key = { it.id },
                emptyTitle = stringResource(R.string.community_empty),
                onRefresh = viewModel::refresh,
                onLoadMore = viewModel::loadMore,
                onRetry = viewModel::load,
                onRetryLoadMore = viewModel::retryLoadMore,
                listState = listState,
                // 글쓰기 버튼에 마지막 카드가 가리지 않게
                contentPadding = PaddingValues(start = PapySpacing.lg, end = PapySpacing.lg, top = PapySize.screenPadding, bottom = PapySize.controlHeight * 2),
                header = {
                    item(key = "header") {
                        PapyScreenHeader(title = stringResource(R.string.community_title), modifier = Modifier.padding(horizontal = PapySpacing.sm))
                    }
                    item(key = "filter") {
                        PapySegmentedTabs(
                            options = listOf(stringResource(R.string.community_filter_all), stringResource(R.string.community_filter_mine)),
                            selectedIndex = state.filter.ordinal,
                            onSelect = { viewModel.filterChanged(CommunityFilter.entries[it]) },
                        )
                    }
                },
                loadingContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(PapySpacing.md)) { repeat(3) { PapySkeletonCard() } }
                },
            ) { post ->
                CommunityPostCard(
                    post = post,
                    onClick = { viewModel.postClicked(post) },
                    onLike = { viewModel.likeClicked(post) },
                    onAuthorClick = { onAuthorSelected(post.author.id) },
                )
            }
            PapyFloatingButton(
                icon = R.drawable.papy_ic_edit,
                contentDescription = stringResource(R.string.community_write),
                onClick = viewModel::composeClicked,
                modifier = Modifier.align(Alignment.BottomEnd).padding(PapySize.screenPadding),
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
    PapyPostCard(
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
        image = { index, modifier -> PapyPostImage(url = post.imageUrls[index], modifier = modifier) },
    )
}
