import StoreKit
import UIKit
import Core

/// StoreKit 리뷰 요청 (KMP `InAppReview` 구현). 표시 여부·횟수는 iOS 가 정한다(개발 빌드는 항상, TestFlight 는 안 뜸).
/// (Android `PlayInAppReview`)
final class StoreKitInAppReview: NSObject, InAppReview {
    func requestReview() {
        Task { @MainActor in
            let scene = UIApplication.shared.connectedScenes
                .first { $0.activationState == .foregroundActive } as? UIWindowScene
            guard let scene else { return }
            AppStore.requestReview(in: scene)
        }
    }
}
