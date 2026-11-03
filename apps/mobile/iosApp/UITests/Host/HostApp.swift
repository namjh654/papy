import SwiftUI

/// UI 테스트 번들이 붙을 빈 앱. 실제 대상은 설치된 앱(번들 ID)이다.
@main
struct HostApp: App {
    var body: some Scene { WindowGroup { Text("ElsaUITests host") } }
}
