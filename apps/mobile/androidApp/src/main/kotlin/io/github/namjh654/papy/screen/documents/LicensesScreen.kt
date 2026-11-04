package io.github.namjh654.papy.screen.documents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import io.github.namjh654.papy.R
import io.github.namjh654.papy.designsystem.component.PapyCard
import io.github.namjh654.papy.designsystem.component.PapyLoadingIndicator
import io.github.namjh654.papy.designsystem.component.PapyScaffold
import io.github.namjh654.papy.designsystem.component.PapyTextField
import io.github.namjh654.papy.designsystem.theme.PapyTheme
import io.github.namjh654.papy.designsystem.token.PapySpacing
import io.github.namjh654.papy.feature.documents.licenses.Notices
import io.github.namjh654.papy.feature.documents.licenses.parseNotices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 오픈소스 라이선스: 앱에 들어 있는 서드파티 구성요소와 라이선스 전문(assets/licenses, 빌드 때 만든다) */
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    // null: 읽는 중, Result.failure: 파일 없음
    val notices by produceState<Result<Notices>?>(null) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open("licenses/THIRD_PARTY_NOTICES.txt").bufferedReader().use { parseNotices(it.readText()) }
            }
        }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var open by rememberSaveable { mutableStateOf<String?>(null) }

    PapyScaffold(title = stringResource(R.string.licenses_title), onBack = onBack, containerColor = PapyTheme.colors.backgroundGrouped) { padding ->
        val loaded = notices
        when {
            loaded == null -> PapyLoadingIndicator(Modifier.fillMaxSize().padding(padding))
            loaded.isFailure -> Text(
                stringResource(R.string.licenses_missing),
                style = PapyTheme.typography.body,
                color = PapyTheme.colors.onSurfaceVariant,
                modifier = Modifier.padding(padding).padding(PapySpacing.lg),
            )
            else -> {
                val all = loaded.getOrThrow().items
                val shown = remember(all, query) { if (query.isBlank()) all else all.filter { it.title.contains(query.trim(), ignoreCase = true) } }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(PapySpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(PapySpacing.sm),
                ) {
                    item(key = "intro") {
                        Column(verticalArrangement = Arrangement.spacedBy(PapySpacing.sm)) {
                            Text(stringResource(R.string.licenses_intro, all.size), style = PapyTheme.typography.body, color = PapyTheme.colors.onSurface)
                            PapyTextField(value = query, onValueChange = { query = it }, placeholder = stringResource(R.string.licenses_search))
                        }
                    }
                    items(shown, key = { it.title }) { notice ->
                        PapyCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(PapySpacing.md)) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { open = if (open == notice.title) null else notice.title },
                            ) {
                                Text(notice.title, style = PapyTheme.typography.subtitle, color = PapyTheme.colors.onSurface)
                                Text(notice.license, style = PapyTheme.typography.caption, color = PapyTheme.colors.onSurfaceVariant)
                                if (open == notice.title) {
                                    Text(
                                        notice.text,
                                        style = PapyTheme.typography.caption,
                                        fontFamily = FontFamily.Monospace,
                                        color = PapyTheme.colors.onSurface,
                                        modifier = Modifier.padding(top = PapySpacing.sm),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
