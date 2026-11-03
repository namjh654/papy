package io.github.namjh654.papy.screen.home

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
import io.github.namjh654.papy.R
import io.github.namjh654.papy.designsystem.component.PapyMenuCard
import io.github.namjh654.papy.designsystem.component.PapyScrollScreen
import io.github.namjh654.papy.designsystem.format.PapyFormat
import io.github.namjh654.papy.designsystem.format.currentLocale
import io.github.namjh654.papy.designsystem.theme.PapyTheme
import io.github.namjh654.papy.designsystem.token.PapySpacing
import io.github.namjh654.papy.feature.home.HomeViewModel
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
    val today = remember(locale) { PapyFormat.dateLong(System.currentTimeMillis(), locale) }

    // 목록·대시보드 화면: 회색 그룹 배경 위에 흰(다크: 짙은 회색) 카드
    PapyScrollScreen {
        Spacer(Modifier.height(PapySpacing.xl))

        Column(verticalArrangement = Arrangement.spacedBy(PapySpacing.xs)) {
            Text(
                text = stringResource(R.string.home_today, today),
                style = PapyTheme.typography.bodySmall,
                color = PapyTheme.colors.onSurfaceVariant,
            )
            Text(
                text = if (state.displayName.isBlank()) {
                    stringResource(R.string.home_greeting_anonymous)
                } else {
                    stringResource(R.string.home_greeting, state.displayName)
                },
                style = PapyTheme.typography.headline,
                color = PapyTheme.colors.onBackground,
            )
        }

        Spacer(Modifier.height(PapySpacing.lg))

        Text(
            text = stringResource(R.string.home_section_menu),
            modifier = Modifier.padding(horizontal = PapySpacing.xs),
            style = PapyTheme.typography.bodyStrong,
            color = PapyTheme.colors.onSurfaceVariant,
        )

        PapyMenuCard(
            title = stringResource(R.string.home_menu_example),
            description = stringResource(R.string.home_menu_example_description),
            onClick = onExampleClick,
        )

        PapyMenuCard(
            title = stringResource(R.string.notice_title),
            description = stringResource(R.string.home_menu_notice_description),
            onClick = onNoticeClick,
        )

        PapyMenuCard(
            title = stringResource(R.string.inbox_title),
            description = stringResource(R.string.home_menu_inbox_description),
            onClick = onInboxClick,
        )

        PapyMenuCard(
            title = stringResource(R.string.search_title),
            description = stringResource(R.string.home_menu_search_description),
            onClick = onSearchClick,
        )

        PapyMenuCard(
            title = stringResource(R.string.permission_title),
            description = stringResource(R.string.home_menu_permission_description),
            onClick = onPermissionClick,
        )

        PapyMenuCard(
            title = stringResource(R.string.home_menu_gallery),
            description = stringResource(R.string.home_menu_gallery_description),
            onClick = onComponentGalleryClick,
        )
    }
}
