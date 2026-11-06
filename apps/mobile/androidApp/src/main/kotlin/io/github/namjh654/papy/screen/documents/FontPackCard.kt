package io.github.namjh654.papy.screen.documents

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.namjh654.papy.R
import io.github.namjh654.papy.designsystem.component.PapyButton
import io.github.namjh654.papy.designsystem.component.PapyButtonSize
import io.github.namjh654.papy.designsystem.component.PapyButtonStyle
import io.github.namjh654.papy.designsystem.component.PapyCard
import io.github.namjh654.papy.designsystem.theme.PapyTheme
import io.github.namjh654.papy.designsystem.token.PapySpacing
import io.github.namjh654.papy.engine.FontPack
import kotlinx.coroutines.launch

private enum class FontPackState { Idle, Downloading, Done, Failed }

/** 명조·굵게 글꼴 받기. 사용자가 누를 때만 받는다(앱은 그 밖에 문서를 네트워크로 보내지 않는다). */
@Composable
fun FontPackCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(if (FontPack.installed(context)) FontPackState.Done else FontPackState.Idle) }
    var progress by remember { mutableFloatStateOf(0f) }
    val megabytes = (FontPack.totalBytes / 1_000_000).toInt()
    PapyCard(modifier = modifier.fillMaxWidth(), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(PapySpacing.xs)) {
            Text(stringResource(R.string.fonts_title), style = PapyTheme.typography.subtitle, color = PapyTheme.colors.onSurface)
            Text(
                text = when (state) {
                    FontPackState.Idle -> stringResource(R.string.fonts_description, megabytes)
                    FontPackState.Downloading -> stringResource(R.string.fonts_downloading, (progress * 100).toInt())
                    FontPackState.Done -> stringResource(R.string.fonts_done)
                    FontPackState.Failed -> stringResource(R.string.fonts_failed)
                },
                style = PapyTheme.typography.caption,
                color = PapyTheme.colors.onSurfaceVariant,
            )
            if (state == FontPackState.Idle || state == FontPackState.Failed) {
                PapyButton(
                    text = stringResource(R.string.fonts_download),
                    onClick = {
                        state = FontPackState.Downloading
                        scope.launch {
                            state = runCatching { FontPack.download(context) { progress = it } }
                                .fold({ FontPackState.Done }, { FontPackState.Failed })
                        }
                    },
                    style = PapyButtonStyle.Secondary,
                    size = PapyButtonSize.Small,
                    fullWidth = false,
                    modifier = Modifier.padding(top = PapySpacing.xs),
                )
            }
    }
}
