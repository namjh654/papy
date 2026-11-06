package io.github.namjh654.papy.screen.documents

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.namjh654.papy.R
import io.github.namjh654.papy.component.EventEffect
import io.github.namjh654.papy.designsystem.component.PapyButton
import io.github.namjh654.papy.designsystem.component.PapyButtonSize
import io.github.namjh654.papy.designsystem.component.PapyButtonStyle
import io.github.namjh654.papy.designsystem.component.PapyCard
import io.github.namjh654.papy.designsystem.component.PapyEmptyState
import io.github.namjh654.papy.designsystem.component.PapyScaffold
import io.github.namjh654.papy.designsystem.component.PapyScreenHeader
import io.github.namjh654.papy.designsystem.theme.PapyTheme
import io.github.namjh654.papy.designsystem.token.PapySpacing
import io.github.namjh654.papy.feature.documents.DocumentKind
import io.github.namjh654.papy.feature.documents.DocumentsEvent
import io.github.namjh654.papy.feature.documents.DocumentsViewModel
import java.text.DateFormat
import java.util.Date
import org.koin.androidx.compose.koinViewModel

/** 첫 화면: 문서 열기 + 최근 문서 */
@Composable
fun DocumentsScreen(onOpen: (ref: String, name: String) -> Unit, onLicenses: () -> Unit) {
    val viewModel: DocumentsViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            context.keepReadAccess(uri)
            viewModel.fileChosen(uri.toString(), context.displayName(uri))
        }
    }

    EventEffect(viewModel.events) { event ->
        when (event) {
            is DocumentsEvent.Open -> onOpen(event.ref, event.name)
        }
    }

    PapyScaffold(containerColor = PapyTheme.colors.backgroundGrouped) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(PapySpacing.lg),
            verticalArrangement = Arrangement.spacedBy(PapySpacing.sm),
        ) {
            item(key = "header") {
                PapyScreenHeader(
                    title = stringResource(R.string.documents_title),
                    description = stringResource(R.string.documents_description),
                )
            }
            item(key = "open") {
                PapyButton(
                    text = stringResource(R.string.documents_open),
                    onClick = { picker.launch(DOCUMENT_MIME_TYPES) },
                    leadingIcon = R.drawable.papy_ic_file,
                )
            }
            if (state.recent.isEmpty() && !state.loading) {
                item(key = "empty") {
                    PapyEmptyState(
                        title = stringResource(R.string.documents_empty),
                        message = stringResource(R.string.documents_empty_message),
                    )
                }
            } else if (state.recent.isNotEmpty()) {
                item(key = "recent") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = PapySpacing.lg),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.documents_recent),
                            style = PapyTheme.typography.subtitle,
                            color = PapyTheme.colors.onBackground,
                            modifier = Modifier.weight(1f),
                        )
                        PapyButton(
                            text = stringResource(R.string.documents_clear),
                            onClick = viewModel::clearRecent,
                            style = PapyButtonStyle.Text,
                            size = PapyButtonSize.Small,
                            fullWidth = false,
                        )
                    }
                }
                items(state.recent, key = { it.ref }) { item ->
                    PapyCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { viewModel.recentClicked(item) },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(PapySpacing.lg),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                                Text(item.name, style = PapyTheme.typography.subtitle, color = PapyTheme.colors.onSurface, maxLines = 2)
                                Text(
                                    text = "${item.kind.label()} · ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(item.openedAt))}",
                                    style = PapyTheme.typography.caption,
                                    color = PapyTheme.colors.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { viewModel.removeRecent(item) }) {
                                Icon(
                                    painter = painterResource(R.drawable.papy_ic_close),
                                    contentDescription = stringResource(R.string.documents_remove),
                                    tint = PapyTheme.colors.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            item(key = "fonts") {
                FontPackCard(modifier = Modifier.padding(top = PapySpacing.xl))
            }
            item(key = "licenses") {
                PapyButton(
                    text = stringResource(R.string.licenses_title),
                    onClick = onLicenses,
                    style = PapyButtonStyle.Text,
                    size = PapyButtonSize.Small,
                )
            }
        }
    }
}

private fun DocumentKind.label() = when (this) {
    DocumentKind.Pdf -> "PDF"
    DocumentKind.Hwp -> "HWP"
    DocumentKind.Hwpx -> "HWPX"
    DocumentKind.Docx -> "DOCX"
    DocumentKind.Doc -> "DOC"
}
