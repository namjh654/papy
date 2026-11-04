import SwiftUI
import Core

/// 문서 앱 흐름: 최근 문서 → 문서 보기. 템플릿의 로그인·탭 흐름(AppNavigation)은 코드만 남겨 둔다(ADR 0006).
struct DocumentsRoot: View {

    struct Opened: Hashable {
        let ref: String
        let name: String
    }

    @State private var path: [Opened] = []
    @State private var showLicenses = false

    var body: some View {
        NavigationStack(path: $path) {
            DocumentsScreen(
                onOpen: { ref, name in path.append(Opened(ref: ref, name: name)) },
                onLicenses: { showLicenses = true }
            )
                .navigationDestination(for: Opened.self) { opened in
                    ViewerScreen(ref: opened.ref, name: opened.name)
                }
                .navigationDestination(isPresented: $showLicenses) { LicensesScreen() }
        }
        #if DEBUG
        // 개발 확인용: `-papyOpen <파일 이름>` 이면 앱 Documents 폴더의 파일을 바로 연다(시뮬레이터 점검).
        .onAppear {
            let arguments = ProcessInfo.processInfo.arguments
            guard let index = arguments.firstIndex(of: "-papyOpen"), index + 1 < arguments.count,
                  let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first
            else { return }
            let url = documents.appendingPathComponent(arguments[index + 1])
            if let ref = PapyDocumentEngine.reference(for: url) {
                path = [Opened(ref: ref, name: url.lastPathComponent)]
            }
        }
        #endif
        #if DEBUG
        // 개발 확인용: `-papyLicenses` 이면 라이선스 화면을 연다
        .task { if ProcessInfo.processInfo.arguments.contains("-papyLicenses") { showLicenses = true } }
        #endif
        // 파일 앱 등에서 "Papy 로 열기"
        .onOpenURL { url in
            guard url.isFileURL, let ref = PapyDocumentEngine.reference(for: url) else { return }
            path = [Opened(ref: ref, name: url.lastPathComponent)]
        }
    }
}
