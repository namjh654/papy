import SwiftUI
import Core

@main
struct iOSApp: App {

    /// 알림 표시·탭 처리 (UNUserNotificationCenterDelegate)
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    init() {
        // 디자인 토큰 글꼴(Pretendard, DEC-117) — 첫 화면보다 먼저
        PapyFont.registerFonts()
        // 서버 이미지 메모리·디스크 캐시(Nuke, DEC-131)
        PapyImagePipeline.configure()
        // 소셜 SDK: env 에 키가 있는 제공자만 초기화(DEC-119)
        IOSSocialSdk.initialize()
        // 푸시·크래시·이벤트: Firebase SDK + GoogleService-Info.plist 가 있으면 켜지고, 없으면 NoOp — 빌드·실행은 같다
        // 환경(API URL 등)은 Build Configuration(xcconfig APP_ENV)으로 빌드된 AppConfig 가 정한다
        IOSAppKoin.shared.initialize(services: PlatformSetup.makeServices())
        // 첫 화면부터 저장된 테마로(없으면 시스템)
        AppTheme.loadSaved()
    }

    var body: some Scene {
        WindowGroup {
            AppNavigation()
                // 시스템 글자 크기를 따르되(DEC-128) 가장 큰 접근성 크기들에서는 레이아웃이 깨지지 않게 여기까지
                .dynamicTypeSize(...DynamicTypeSize.accessibility1)
        }
    }
}
