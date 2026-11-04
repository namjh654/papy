import SwiftUI
import Core

@main
struct iOSApp: App {

    init() {
        // 디자인 토큰 글꼴(Pretendard, DEC-117) — 첫 화면보다 먼저
        PapyFont.registerFonts()
        // 환경(API URL 등)은 Build Configuration(xcconfig APP_ENV)으로 빌드된 AppConfig 가 정한다
        IOSAppKoin.shared.initialize()
        // 문서 엔진: Rust(HWP·HWPX·DOCX) + PDFKit(PDF)
        IOSAppKoin.shared.registerDocumentEngine(engine: PapyDocumentEngine.shared)
        // 첫 화면부터 저장된 테마로(없으면 시스템)
        AppTheme.loadSaved()
    }

    var body: some Scene {
        WindowGroup {
            // 문서 앱 흐름: 최근 문서 → 문서 보기(ADR 0006)
            DocumentsRoot()
                // 시스템 글자 크기를 따르되(DEC-128) 가장 큰 접근성 크기들에서는 레이아웃이 깨지지 않게 여기까지
                .dynamicTypeSize(...DynamicTypeSize.accessibility1)
        }
    }
}
