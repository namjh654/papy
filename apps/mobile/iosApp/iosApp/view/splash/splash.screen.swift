import SwiftUI
import Core

struct SplashScreen: View {

    let onSessionRestored: () -> Void
    let onSessionNotFound: () -> Void
    /// 처음 실행(온보딩을 아직 보지 않음)
    let onFirstLaunch: () -> Void

    @StateObject private var owner = ViewModelOwner(
        IOSAppGraph.shared.splashViewModel()
    )

    var body: some View {
        ElsaLoadingIndicator()
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .elsaScreenBackground()
            .task {
                await observeEvents()
            }
    }
}


// MARK: - Event Observation

private extension SplashScreen {

    func observeEvents() async {
        for await event in owner.viewModel.events {
            switch onEnum(of: event) {
            case .sessionRestored:
                onSessionRestored()
            case .sessionNotFound:
                onSessionNotFound()
            case .firstLaunch:
                onFirstLaunch()
            }
        }
    }
}
