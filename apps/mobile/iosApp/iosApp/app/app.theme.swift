import UIKit
import Core

/// 설정 탭의 테마를 **창 단위**로 적용한다 → 본문·시트·알림이 같은 기준. 다크 여부는 KMP `ThemeMode.forcedDark`
/// (nil 이면 시스템). SwiftUI `.preferredColorScheme(nil)` 은 강제했던 값을 바로 풀지 않는 경우가 있어 쓰지 않는다.
/// (Android `withAppSettings` + `syncAppNightMode`)
/// 한계: 앱 실행 직전 iOS 가 그리는 런치 스크린은 시스템 모드를 따른다.
enum AppTheme {

    /// 앱 시작 때 한 번: 저장값을 읽을 때까지(최대 0.5초) 기다려 첫 화면부터 맞는 테마로 그린다
    static func loadSaved() {
        let settings = IOSAppGraph.shared.appSettings()
        let done = DispatchSemaphore(value: 0)
        Task.detached {
            try? await settings.ensureLoaded()
            done.signal()
        }
        _ = done.wait(timeout: .now() + 0.5)
    }

    static func apply(_ mode: ThemeMode) {
        let style: UIUserInterfaceStyle = switch mode.forcedDark?.boolValue {
        case .some(true): .dark
        case .some(false): .light
        case nil: .unspecified
        }
        for case let scene as UIWindowScene in UIApplication.shared.connectedScenes {
            for window in scene.windows {
                window.overrideUserInterfaceStyle = style
            }
        }
    }
}
