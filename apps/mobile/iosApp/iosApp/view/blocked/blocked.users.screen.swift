import SwiftUI
import Core

/// 설정 → 차단한 사용자(App Store 1.2): 목록과 차단 해제. (Android `BlockedUsersScreen`)
struct BlockedUsersScreen: View {

    @StateObject private var model = SharedViewModel(
        IOSAppGraph.shared.blockedUsersViewModel(),
        state: \.uiState
    )
    @State private var toast: ElsaToast?

    private var state: BlockedUsersUiState { model.state }
    private var users: [Author] { state.users.compactMap { $0 as? Author } }

    var body: some View {
        Group {
            if state.isLoading && users.isEmpty {
                ElsaLoadingIndicator().frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if let error = state.error, users.isEmpty {
                ElsaErrorState(message: error.localizedMessage, onRetry: { model.viewModel.load() })
                    .frame(maxHeight: .infinity)
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        ElsaScreenHeader(title: String(localized: "blocked_title"))
                            .padding(ElsaSize.screenPadding)
                        if users.isEmpty {
                            ElsaEmptyState(title: String(localized: "blocked_empty"))
                        }
                        ForEach(users, id: \.id) { user in
                            ElsaListItem(
                                title: user.name,
                                leading: { ElsaAvatar(name: user.name, url: user.avatarUrl, size: 40) },
                                trailing: {
                                    ElsaButton(String(localized: "blocked_unblock"), style: .secondary, size: .small, fullWidth: false) {
                                        model.viewModel.unblock(user: user)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
        .elsaScreenBackground()
        .navigationBarTitleDisplayMode(.inline)
        .elsaToast($toast)
        .task {
            for await event in model.viewModel.events {
                switch onEnum(of: event) {
                case .unblocked:
                    toast = ElsaToast(message: String(localized: "blocked_unblocked"), type: .success)
                }
            }
        }
    }
}
