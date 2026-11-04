package io.github.namjh654.papy.screen.documents

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

/** 문서 앱 화면 경로(템플릿의 로그인·탭 흐름 대신 쓴다, ADR 0006) */
sealed interface DocumentsRoute {
    @Serializable
    data object Home : DocumentsRoute

    @Serializable
    data class Viewer(val ref: String, val name: String) : DocumentsRoute

    @Serializable
    data object Licenses : DocumentsRoute
}

/** 다른 앱에서 "Papy 로 열기"로 넘어온 문서(MainActivity 가 넣고 화면이 꺼낸다) */
object IncomingDocuments {
    private val _pending = MutableStateFlow<Pair<Uri, String>?>(null)
    val pending: StateFlow<Pair<Uri, String>?> = _pending.asStateFlow()

    fun offer(uri: Uri, name: String) {
        _pending.value = uri to name
    }

    fun take(): Pair<Uri, String>? = _pending.value.also { _pending.value = null }
}

@Composable
fun DocumentsNavHost() {
    val navController = rememberNavController()
    val incoming by IncomingDocuments.pending.collectAsStateWithLifecycle()
    LaunchedEffect(incoming) {
        IncomingDocuments.take()?.let { (uri, name) ->
            navController.navigate(DocumentsRoute.Viewer(uri.toString(), name)) { launchSingleTop = true }
        }
    }
    NavHost(navController = navController, startDestination = DocumentsRoute.Home) {
        composable<DocumentsRoute.Home> {
            DocumentsScreen(
                onOpen = { ref, name -> navController.navigate(DocumentsRoute.Viewer(ref, name)) },
                onLicenses = { navController.navigate(DocumentsRoute.Licenses) },
            )
        }
        composable<DocumentsRoute.Viewer> { entry ->
            val route = entry.toRoute<DocumentsRoute.Viewer>()
            ViewerScreen(ref = route.ref, name = route.name, onBack = { navController.popBackStack() })
        }
        composable<DocumentsRoute.Licenses> {
            LicensesScreen(onBack = { navController.popBackStack() })
        }
    }
}
