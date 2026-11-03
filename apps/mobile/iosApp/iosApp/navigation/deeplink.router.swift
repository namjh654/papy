import Foundation
import Core

/// DeepLink(path) → iOS 메인 화면. Android `DeepLinkRouter` 와 같은 path 를 쓴다.
enum DeepLinkRouter {

    enum Destination: Equatable {
        /// 탭으로 이동(그 탭의 스택은 비운다)
        case tab(MainTab)
        /// 지금 탭 위에 한 화면
        case route(MainRoute)
    }

    /// 모르는 path 는 nil (현재 화면 유지)
    static func destination(for deepLink: DeepLink) -> Destination? {
        // 인자가 있는 화면: <path>/<id>
        let parts = deepLink.path.split(separator: "/", maxSplits: 1).map(String.init)
        if parts.count == 2 {
            switch parts[0] {
            case "notice": return .route(.noticeDetail(id: parts[1]))
            case "example": return .route(.exampleDetail(id: parts[1]))
            // 이름은 대화방이 불러온다(21-3)
            case "chat": return .route(.chatRoom(id: parts[1], title: ""))
            case "community": return .route(.postDetail(id: parts[1]))
            // scaffold:deeplink-details — 상세 화면 path 는 이 줄 위에
            default: return nil
            }
        }
        switch deepLink.path {
        case "home": return .tab(.home)
        case "settings": return .tab(.settings)
        case "gallery": return .route(.componentGallery)
        case "example": return .tab(.example)
        case "notice": return .route(.notice)
        case "permission": return .route(.permission)
        case "inbox": return .route(.inbox)
        case "search": return .route(.search)
        case "chat": return .tab(.chat)
        case "community": return .tab(.community)
        // scaffold:deeplinks — newFeature 가 이 줄 위에 path 를 추가한다
        default: return nil
        }
    }
}
