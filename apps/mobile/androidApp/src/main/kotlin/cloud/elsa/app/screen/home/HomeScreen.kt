package cloud.elsa.app.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cloud.elsa.app.R
import cloud.elsa.app.designsystem.component.ElsaMenuCard
import cloud.elsa.app.designsystem.component.ElsaScrollScreen
import cloud.elsa.app.designsystem.format.ElsaFormat
import cloud.elsa.app.designsystem.format.currentLocale
import cloud.elsa.app.designsystem.theme.ElsaTheme
import cloud.elsa.app.designsystem.token.ElsaSpacing
import cloud.elsa.app.feature.home.HomeViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun HomeScreen(
    onComponentGalleryClick: () -> Unit,
    onExampleClick: () -> Unit,
    onNoticeClick: () -> Unit,
    onInboxClick: () -> Unit,
    onSearchClick: () -> Unit,
    onPermissionClick: () -> Unit,
) {
    val viewModel: HomeViewModel = koinViewModel()

    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val locale = currentLocale
    val today = remember(locale) { ElsaFormat.dateLong(System.currentTimeMillis(), locale) }

    // 목록·대시보드 화면: 회색 그룹 배경 위에 흰(다크: 짙은 회색) 카드
    ElsaScrollScreen {
        Spacer(Modifier.height(ElsaSpacing.xl))

        Column(verticalArrangement = Arrangement.spacedBy(ElsaSpacing.xs)) {
            Text(
                text = stringResource(R.string.home_today, today),
                style = ElsaTheme.typography.bodySmall,
                color = ElsaTheme.colors.onSurfaceVariant,
            )
            Text(
                text = if (state.displayName.isBlank()) {
                    stringResource(R.string.home_greeting_anonymous)
                } else {
                    stringResource(R.string.home_greeting, state.displayName)
                },
                style = ElsaTheme.typography.headline,
                color = ElsaTheme.colors.onBackground,
            )
        }

        Spacer(Modifier.height(ElsaSpacing.lg))

        Text(
            text = stringResource(R.string.home_section_menu),
            modifier = Modifier.padding(horizontal = ElsaSpacing.xs),
            style = ElsaTheme.typography.bodyStrong,
            color = ElsaTheme.colors.onSurfaceVariant,
        )

        ElsaMenuCard(
            title = stringResource(R.string.home_menu_example),
            description = stringResource(R.string.home_menu_example_description),
            onClick = onExampleClick,
        )

        ElsaMenuCard(
            title = stringResource(R.string.notice_title),
            description = stringResource(R.string.home_menu_notice_description),
            onClick = onNoticeClick,
        )

        ElsaMenuCard(
            title = stringResource(R.string.inbox_title),
            description = stringResource(R.string.home_menu_inbox_description),
            onClick = onInboxClick,
        )

        ElsaMenuCard(
            title = stringResource(R.string.search_title),
            description = stringResource(R.string.home_menu_search_description),
            onClick = onSearchClick,
        )

        ElsaMenuCard(
            title = stringResource(R.string.permission_title),
            description = stringResource(R.string.home_menu_permission_description),
            onClick = onPermissionClick,
        )

        ElsaMenuCard(
            title = stringResource(R.string.home_menu_gallery),
            description = stringResource(R.string.home_menu_gallery_description),
            onClick = onComponentGalleryClick,
        )
    }
}
