package cloud.elsa.app.screen.splash

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cloud.elsa.app.component.EventEffect
import cloud.elsa.app.designsystem.component.ElsaLoadingIndicator
import cloud.elsa.app.feature.auth.splash.SplashEvent
import cloud.elsa.app.feature.auth.splash.SplashViewModel
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
        ElsaLoadingIndicator()
    }
}
