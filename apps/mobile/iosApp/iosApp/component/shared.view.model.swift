import Foundation
import Core

/// KMP ViewModel의 수명을 SwiftUI 화면 수명에 맞춥니다.
///
/// `@StateObject`로 보관하면 화면이 사라질 때 `dispose()`가 호출되어
/// Kotlin `viewModelScope`가 취소됩니다.
@MainActor
final class ViewModelOwner<VM: BaseViewModel>: ObservableObject {

    let viewModel: VM

    init(_ viewModel: VM) {
        self.viewModel = viewModel
    }

    deinit {
        viewModel.dispose()
    }
}

/// KMP ViewModel + UiState(StateFlow)를 SwiftUI에 연결합니다.
///
/// SKIE가 `StateFlow<T>`를 `SkieSwiftStateFlow<T>`로 변환하므로
/// 캐스팅 없이 Kotlin UiState 타입을 그대로 사용합니다.
@MainActor
final class SharedViewModel<VM: BaseViewModel, State>: ObservableObject {

    let viewModel: VM

    @Published private(set) var state: State

    private let flow: SkieSwiftStateFlow<State>

    private var task: Task<Void, Never>?

    init(
        _ viewModel: VM,
        state keyPath: KeyPath<VM, SkieSwiftStateFlow<State>>
    ) {
        let flow = viewModel[keyPath: keyPath]

        self.viewModel = viewModel
        self.flow = flow
        self.state = flow.value

        task = Task { [weak self] in
            for await next in flow {
                self?.state = next
            }
        }
    }

    deinit {
        task?.cancel()
        viewModel.dispose()
    }

    /// KMP 상태가 조건을 만족할 때까지 기다린다. 당겨서 새로고침(`.refreshable`)이 끝날 때까지 스피너를 유지하는 데 쓴다:
    /// `onRefresh: { model.viewModel.refresh(); await model.waitUntil { !$0.list.isRefreshing } }`
    func waitUntil(_ predicate: (State) -> Bool) async {
        for await next in flow where predicate(next) {
            return
        }
    }
}
