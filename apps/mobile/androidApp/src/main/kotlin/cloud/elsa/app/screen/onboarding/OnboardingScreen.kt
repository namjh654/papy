package cloud.elsa.app.screen.onboarding

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cloud.elsa.app.R
import cloud.elsa.app.component.EventEffect
import cloud.elsa.app.designsystem.component.ElsaButton
import cloud.elsa.app.designsystem.component.ElsaButtonSize
import cloud.elsa.app.designsystem.component.ElsaButtonStyle
import cloud.elsa.app.designsystem.component.ElsaPageIndicator
import cloud.elsa.app.designsystem.theme.ElsaTheme
import cloud.elsa.app.designsystem.token.ElsaSize
import cloud.elsa.app.designsystem.token.ElsaSpacing
import cloud.elsa.app.feature.auth.onboarding.OnboardingEvent
import cloud.elsa.app.feature.auth.onboarding.OnboardingViewModel
import org.koin.androidx.compose.koinViewModel

private class OnboardingPage(@DrawableRes val icon: Int, @StringRes val title: Int, @StringRes val description: Int)

/** 쪽 내용. 쪽 수는 KMP `OnboardingUiState.PAGE_COUNT` 와 같아야 한다 */
private val pages = listOf(
    OnboardingPage(R.drawable.elsa_ic_tab_home, R.string.onboarding_1_title, R.string.onboarding_1_description),
    OnboardingPage(R.drawable.elsa_ic_tab_list, R.string.onboarding_2_title, R.string.onboarding_2_description),
    OnboardingPage(R.drawable.elsa_ic_tab_settings, R.string.onboarding_3_title, R.string.onboarding_3_description),
)

/** 온보딩(처음 실행 때 한 번): 밀어서 넘기는 쪽 + 쪽 표시 점 + 하단 고정 버튼, 오른쪽 위 건너뛰기 */
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
) {
    val viewModel: OnboardingViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(initialPage = state.page) { state.pageCount }

    EventEffect(viewModel.events) { event ->
        when (event) {
            OnboardingEvent.Done -> onDone()
        }
    }

    // 버튼·뒤로가기로 바뀐 쪽 → pager
    LaunchedEffect(state.page) {
        if (pagerState.currentPage != state.page) pagerState.animateScrollToPage(state.page)
    }
    // 밀어서 바뀐 쪽 → ViewModel
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect(viewModel::pageChanged)
    }
    // 첫 쪽이 아니면 뒤로가기는 앞 쪽으로(첫 쪽에서는 앱을 나간다)
    BackHandler(enabled = state.page > 0, onBack = viewModel::previous)

    Column(modifier = Modifier.fillMaxSize().padding(ElsaSize.screenPadding)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            ElsaButton(
                text = stringResource(R.string.onboarding_skip),
                onClick = viewModel::skip,
                style = ElsaButtonStyle.Text,
                size = ElsaButtonSize.Small,
                fullWidth = false,
            )
        }

        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { index ->
            val page = pages[index]
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ElsaSpacing.xl, Alignment.CenterVertically),
            ) {
                Box(
                    modifier = Modifier.size(120.dp).clip(CircleShape).background(ElsaTheme.colors.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(page.icon),
                        contentDescription = null,
                        tint = ElsaTheme.colors.onPrimaryContainer,
                        modifier = Modifier.size(56.dp),
                    )
                }
                Text(
                    text = stringResource(page.title),
                    style = ElsaTheme.typography.headline,
                    color = ElsaTheme.colors.onBackground,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(page.description),
                    style = ElsaTheme.typography.body,
                    color = ElsaTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        ElsaPageIndicator(
            pageCount = state.pageCount,
            currentPage = state.page,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = ElsaSpacing.xl),
        )

        ElsaButton(
            text = stringResource(if (state.isLastPage) R.string.onboarding_start else R.string.onboarding_next),
            onClick = viewModel::next,
        )
    }
}
