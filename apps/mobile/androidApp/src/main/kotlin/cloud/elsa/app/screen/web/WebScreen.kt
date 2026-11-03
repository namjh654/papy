package cloud.elsa.app.screen.web

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import cloud.elsa.app.R
import cloud.elsa.app.designsystem.component.ElsaErrorState
import cloud.elsa.app.designsystem.component.ElsaLoadingIndicator
import cloud.elsa.app.designsystem.component.ElsaScaffold
import cloud.elsa.app.designsystem.theme.ElsaTheme

/**
 * 웹 페이지 공통 화면(약관·공지). 신뢰하는 자체 페이지만 연다 — 외부 링크는 브라우저로 보낸다.
 * 페이지 안 뒤로가기가 있으면 먼저 웹 기록을 되돌린다.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebScreen(
    url: String,
    title: String,
    onBack: () -> Unit,
) {
    var isLoading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    BackHandler(enabled = canGoBack) { webView?.goBack() }

    ElsaScaffold(title = title, onBack = onBack) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                isLoading = true
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                isLoading = false
                                canGoBack = view.canGoBack()
                            }

                            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                if (request.isForMainFrame) failed = true
                            }
                        }
                        loadUrl(url)
                        webView = this
                    }
                },
                onRelease = { it.destroy() },
            )

            if (failed) {
                // WebView 기본 오류 페이지를 가린다
                Box(Modifier.fillMaxSize().background(ElsaTheme.colors.background), contentAlignment = Alignment.Center) {
                    ElsaErrorState(
                        message = stringResource(R.string.web_error),
                        onRetry = {
                            failed = false
                            webView?.reload()
                        },
                    )
                }
            } else if (isLoading) {
                ElsaLoadingIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}
