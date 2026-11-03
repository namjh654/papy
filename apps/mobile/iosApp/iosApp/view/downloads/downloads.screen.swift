import QuickLook
import SwiftUI
import Core

/// 설정 → 저장한 파일(30-1): 받아 둔 파일(이름·크기·받은 날짜), 누르면 미리 보기(QuickLook), 더보기 → 공유·지우기, 위 "모두 지우기". (Android `DownloadsScreen`)
struct DownloadsScreen: View {

    @StateObject private var model = SharedViewModel(
        IOSAppGraph.shared.downloadsViewModel(),
        state: \.uiState
    )
    @State private var toast: PapyToast?
    @State private var alert: PapyAlert?
    @State private var menuFor: DownloadedFile?
    @State private var quickLook: URL?
    @State private var shareFile: ShareItem?
    @Environment(\.locale) private var locale

    private var viewModel: DownloadsViewModel { model.viewModel }
    private var state: DownloadsUiState { model.state }

    var body: some View {
        content
            .papyScreenBackground()
            .navigationBarTitleDisplayMode(.inline)
            .toolbar(.hidden, for: .tabBar)
            .toolbar {
                if !state.files.isEmpty {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button {
                            alert = PapyAlert(
                                title: String(localized: "file_delete_all_title"),
                                confirmText: String(localized: "file_delete_all"),
                                dismissText: String(localized: "common_cancel"),
                                isDestructive: true,
                                onConfirm: { viewModel.deleteAll() }
                            )
                        } label: { PapyIcon("papy_ic_delete") }
                            .accessibilityLabel(String(localized: "file_delete_all"))
                    }
                }
            }
            .papyToast($toast)
            .papyAlert($alert)
            .quickLookPreview($quickLook)
            .sheet(item: $shareFile) { item in ShareSheet(url: item.url) }
            .papyActionSheet(
                isPresented: Binding(get: { menuFor != nil }, set: { if !$0 { menuFor = nil } }),
                title: menuFor?.name,
                actions: menuActions
            )
            .task {
                for await event in viewModel.events {
                    switch onEnum(of: event) {
                    case .open(let open): quickLook = URL(fileURLWithPath: open.file.path)
                    case .share(let share): shareFile = ShareItem(url: URL(fileURLWithPath: share.file.path))
                    case .deleted: toast = PapyToast(message: String(localized: "file_deleted"), type: .success)
                    }
                }
            }
    }

    @ViewBuilder
    private var content: some View {
        if state.isLoading {
            PapyLoadingIndicator().frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    PapyScreenHeader(
                        title: String(localized: "file_title"),
                        description: String(format: String(localized: "file_description"), PapyFormat.fileSize(state.totalSize, locale: locale))
                    )
                    .padding(PapySize.screenPadding)
                    if state.files.isEmpty {
                        PapyEmptyState(title: String(localized: "file_empty"))
                    }
                    ForEach(state.files, id: \.url) { file in
                        PapyListItem(
                            title: file.name,
                            subtitle: "\(PapyFormat.fileSize(file.size, locale: locale)) · \(PapyFormat.date(epochMillis: file.savedAt, locale: locale))",
                            action: { viewModel.open(file: file) },
                            leading: { PapyIcon("papy_ic_file").foregroundStyle(PapyColor.onSurfaceVariant) },
                            trailing: {
                                Button { menuFor = file } label: {
                                    PapyIcon("papy_ic_more").foregroundStyle(PapyColor.onSurfaceTertiary)
                                        .frame(width: PapySize.controlHeightSmall, height: PapySize.controlHeightSmall)
                                }
                                .accessibilityLabel(String(localized: "community_more"))
                            }
                        )
                    }
                }
            }
        }
    }

    private var menuActions: [PapyAction] {
        guard let file = menuFor else { return [] }
        return [
            PapyAction(label: String(localized: "file_open")) { viewModel.open(file: file) },
            PapyAction(label: String(localized: "file_share")) { viewModel.share(file: file) },
            PapyAction(label: String(localized: "file_delete"), isDestructive: true) { viewModel.delete(file: file) },
        ]
    }
}

/// 공유 시트에 넘길 파일(sheet(item:) 용)
struct ShareItem: Identifiable {
    let url: URL
    var id: URL { url }
}

/// 시스템 공유 시트: 다른 앱으로 보내기·"파일에 저장"
struct ShareSheet: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: [url], applicationActivities: nil)
    }

    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}
