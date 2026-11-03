package io.github.namjh654.papy.screen.splash

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.namjh654.papy.component.EventEffect
import io.github.namjh654.papy.designsystem.component.PapyLoadingIndicator
import io.github.namjh654.papy.feature.auth.splash.SplashEvent
import io.github.namjh654.papy.feature.auth.splash.SplashViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun SplashScreen(
    onSessionRestored: () -> Unit,
    onSessionNotFound: () -> Unit,
    onFirstLaunch: () -> Unit,
) {
    val viewModel: SplashViewModel = koinViewModel()

    EventEffect(viewModel.events) { event ->
        when (event) {
            is SplashEvent.SessionRestored -> onSessionRestored()
            SplashEvent.SessionNotFound -> onSessionNotFound()
            SplashEvent.FirstLaunch -> onFirstLaunch()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        PapyLoadingIndicator()
    }
}
