import SwiftUI
import WebKit

/// 웹 페이지 공통 화면(약관·공지). 신뢰하는 자체 페이지만 연다. (Android `WebScreen`)
struct WebScreen: View {

    let url: URL
    let title: String

    @State private var isLoading = true
    @State private var failed = false
    @State private var reloadToken = 0

    var body: some View {
        ZStack {
            WebView(url: url, reloadToken: reloadToken, isLoading: $isLoading, failed: $failed)
                .ignoresSafeArea(edges: .bottom)

            if failed {
                ElsaErrorState(message: String(localized: "web_error")) {
                    failed = false
                    reloadToken += 1
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(ElsaColor.background)
            } else if isLoading {
                ElsaLoadingIndicator()
            }
        }
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
    }
}

/// WKWebView 래퍼. `reloadToken` 이 바뀌면 다시 불러온다.
private struct WebView: UIViewRepresentable {
    let url: URL
    let reloadToken: Int
    @Binding var isLoading: Bool
    @Binding var failed: Bool

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeUIView(context: Context) -> WKWebView {
        let view = WKWebView()
        view.navigationDelegate = context.coordinator
        view.allowsBackForwardNavigationGestures = true
        view.load(URLRequest(url: url))
        context.coordinator.loadedToken = reloadToken
        return view
    }

    func updateUIView(_ view: WKWebView, context: Context) {
        context.coordinator.parent = self
        if context.coordinator.loadedToken != reloadToken {
            context.coordinator.loadedToken = reloadToken
            view.load(URLRequest(url: url))
        }
    }

    final class Coordinator: NSObject, WKNavigationDelegate {
        var parent: WebView
        var loadedToken = 0

        init(_ parent: WebView) { self.parent = parent }

        func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
            parent.isLoading = true
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            parent.isLoading = false
        }

        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
            parent.isLoading = false
            parent.failed = true
        }

        func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
            parent.isLoading = false
            parent.failed = true
        }
    }
}
