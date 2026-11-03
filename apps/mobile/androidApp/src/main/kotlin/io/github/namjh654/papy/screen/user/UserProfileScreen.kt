package io.github.namjh654.papy.screen.user

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.namjh654.papy.R
import io.github.namjh654.papy.component.EventEffect
import io.github.namjh654.papy.component.message
import io.github.namjh654.papy.designsystem.component.PapyAction
import io.github.namjh654.papy.designsystem.component.PapyActionSheet
import io.github.namjh654.papy.designsystem.component.PapyAlert
import io.github.namjh654.papy.designsystem.component.PapyAlertDialog
import io.github.namjh654.papy.designsystem.component.PapyAvatar
import io.github.namjh654.papy.designsystem.component.PapyButton
import io.github.namjh654.papy.designsystem.component.PapyErrorState
import io.github.namjh654.papy.designsystem.component.PapyLoadingIndicator
import io.github.namjh654.papy.designsystem.component.PapyMessageType
import io.github.namjh654.papy.designsystem.component.PapyPagedList
import io.github.namjh654.papy.designsystem.component.PapyScaffold
import io.github.namjh654.papy.designsystem.component.rememberPapyToastState
import io.github.namjh654.papy.designsystem.theme.PapyTheme
import io.github.namjh654.papy.designsystem.token.PapyRadius
import io.github.namjh654.papy.designsystem.token.PapySize
import io.github.namjh654.papy.designsystem.token.PapySpacing
import io.github.namjh654.papy.feature.community.Post
import io.github.namjh654.papy.feature.community.UserProfile
import io.github.namjh654.papy.feature.community.user.UserProfileEvent
import io.github.namjh654.papy.feature.community.user.UserProfileViewModel
import io.github.namjh654.papy.screen.community.CommunityPostCard
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * 사용자 프로필(25-1): 글쓴이를 누르면 열린다. 위에 프로필 카드(사진·이름·소개·글 수·메시지 보내기), 아래에 그 사람이 쓴 글.
 * 오른쪽 위 더보기 → 차단(확인 뒤 닫힌다). 내 프로필이면 메시지·차단이 없다. iOS `user.profile.screen.swift`
 */
@Composable
fun UserProfileScreen(
    userId: String,
    onPostSelected: (Post) -> Unit,
    onMessage: (userId: String, name: String) -> Unit,
    onClosed: () -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: UserProfileViewModel = koinViewModel(key = "user-$userId") { parametersOf(userId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val toast = rememberPapyToastState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var alert by remember { mutableStateOf<PapyAlert?>(null) }

    EventEffect(viewModel.events) { event ->
        when (event) {
            is UserProfileEvent.PostSelected -> onPostSelected(event.post)
            is UserProfileEvent.MessageRequested -> onMessage(event.userId, event.name)
            UserProfileEvent.Blocked -> onClosed()
            is UserProfileEvent.ActionFailed -> scope.launch { toast.show(event.error.message(context), PapyMessageType.Error) }
        }
    }

    val blockTitle = stringResource(R.string.community_block_title)
    val blockMessage = stringResource(R.string.community_block_message)
    val blockText = stringResource(R.string.community_block)

    PapyScaffold(
        onBack = onBack,
        toastState = toast,
        containerColor = PapyTheme.colors.backgroundGrouped,
        actions = {
            if (state.canInteract) {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(painter = painterResource(R.drawable.papy_ic_more), contentDescription = stringResource(R.string.community_more), tint = PapyTheme.colors.onSurface)
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            val profile = state.profile
            when {
                profile == null && state.error != null ->
                    PapyErrorState(message = state.error!!.message(), onRetry = viewModel::load, modifier = Modifier.align(Alignment.Center))
                profile == null -> PapyLoadingIndicator(modifier = Modifier.align(Alignment.Center))
                else -> PapyPagedList(
                    state = state.posts,
                    key = { it.id },
                    emptyTitle = stringResource(R.string.user_profile_no_posts),
                    onRefresh = viewModel::refresh,
                    onLoadMore = viewModel::loadMore,
                    onRetry = viewModel::refresh,
                    onRetryLoadMore = viewModel::retryLoadMore,
                    contentPadding = PaddingValues(start = PapySpacing.lg, end = PapySpacing.lg, top = PapySpacing.sm, bottom = PapySize.screenPadding),
                    header = {
                        item(key = "profile") {
                            UserProfileCard(profile = profile, onMessage = viewModel::messageClicked)
                        }
                    },
                ) { post ->
                    CommunityPostCard(
                        post = post,
                        onClick = { viewModel.postClicked(post) },
                        onLike = { viewModel.likeClicked(post) },
                        onAuthorClick = null,
                    )
                }
            }
        }
    }

    if (menuOpen) {
        PapyActionSheet(
            actions = listOf(
                PapyAction(label = blockText, destructive = true) {
                    alert = PapyAlert(title = blockTitle, message = blockMessage, confirmText = blockText, dismissText = context.getString(R.string.common_cancel), isDestructive = true, onConfirm = viewModel::block)
                },
            ),
            onDismiss = { menuOpen = false },
        )
    }
    PapyAlertDialog(alert = alert, onDismiss = { alert = null })
}

/** 프로필 카드(미리보기 대상): 가운데 사진 88 → 이름(title) → 소개 → 글 수 → 메시지 보내기(내 프로필이면 없음) */
@Composable
fun UserProfileCard(profile: UserProfile, onMessage: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = PapySpacing.md)
            .clip(RoundedCornerShape(PapyRadius.xl))
            .background(PapyTheme.colors.surface)
            .padding(PapySpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PapySpacing.sm),
    ) {
        PapyAvatar(name = profile.name, url = profile.avatarUrl, size = 88.dp)
        Text(text = profile.name, style = PapyTheme.typography.title, color = PapyTheme.colors.onSurface, textAlign = TextAlign.Center)
        profile.bio?.takeIf { it.isNotBlank() }?.let {
            Text(text = it, style = PapyTheme.typography.body, color = PapyTheme.colors.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        Text(text = stringResource(R.string.user_profile_post_count, profile.postCount), style = PapyTheme.typography.caption, color = PapyTheme.colors.onSurfaceTertiary)
        if (!profile.isMe) {
            PapyButton(
                text = stringResource(R.string.user_profile_message),
                onClick = onMessage,
                leadingIcon = R.drawable.papy_ic_chat,
                modifier = Modifier.padding(top = PapySpacing.sm),
            )
        }
    }
}

@Preview(name = "라이트")
@Preview(name = "다크", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun UserProfileCardPreview() = PapyTheme {
    Column(modifier = Modifier.background(PapyTheme.colors.backgroundGrouped).padding(PapySpacing.lg)) {
        UserProfileCard(UserProfile(id = "u1", name = "엘사", bio = "겨울을 좋아해요.", postCount = 12), onMessage = {})
        UserProfileCard(UserProfile(id = "me", name = "나", postCount = 3, isMe = true), onMessage = {})
    }
}
