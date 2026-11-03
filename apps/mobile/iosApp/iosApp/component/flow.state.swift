import Foundation
import Core

/// KMP `StateFlow<T>`(SKIE `SkieSwiftStateFlow<T>`) 하나를 SwiftUI 에 연결한다.
/// 앱 전역 상태(네트워크·테마 등)를 루트에서 관찰할 때 쓴다: `@StateObject var online = FlowState(IOSAppGraph.shared.networkMonitor().isOnline)`
@MainActor
final class FlowState<T>: ObservableObject {

    @Published private(set) var value: T

    private var task: Task<Void, Never>?

    init(_ flow: SkieSwiftStateFlow<T>) {
        value = flow.value
        task = Task { [weak self] in
            for await next in flow {
                self?.value = next
            }
        }
    }

    /// `StateFlow<T?>`(SKIE `SkieSwiftOptionalStateFlow`) — 예: 운영 공지
    init<U>(_ flow: SkieSwiftOptionalStateFlow<U>) where T == U? {
        value = flow.value
        task = Task { [weak self] in
            for await next in flow {
                self?.value = next
            }
        }
    }

    deinit {
        task?.cancel()
    }
}
