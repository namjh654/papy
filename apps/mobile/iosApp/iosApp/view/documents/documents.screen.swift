import SwiftUI
import UniformTypeIdentifiers
import Core

/// 첫 화면: 문서 열기 + 최근 문서. (Android `DocumentsScreen`)
struct DocumentsScreen: View {

    let onOpen: (String, String) -> Void
    let onLicenses: () -> Void

    @StateObject private var model = SharedViewModel(IOSAppGraph.shared.documentsViewModel(), state: \.uiState)
    @State private var picking = false

    /// 고를 수 있는 형식. HWP·HWPX 는 시스템에 형식이 없어 확장자로 만든다.
    private static let types: [UTType] = [
        .pdf,
        UTType(filenameExtension: "hwp"),
        UTType(filenameExtension: "hwpx"),
        UTType(filenameExtension: "docx"),
    ].compactMap { $0 }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: PapySpacing.sm) {
                PapyScreenHeader(
                    title: String(localized: "documents_title"),
                    description: String(localized: "documents_description")
                )
                PapyButton(String(localized: "documents_open"), icon: "papy_ic_file") { picking = true }
                    .accessibilityIdentifier("documents_open")
                if model.state.recent.isEmpty && !model.state.loading {
                    PapyEmptyState(
                        title: String(localized: "documents_empty"),
                        message: String(localized: "documents_empty_message")
                    )
                } else if !model.state.recent.isEmpty {
                    HStack {
                        Text(String(localized: "documents_recent"))
                            .papyFont(PapyFont.subtitle)
                            .foregroundStyle(PapyColor.onBackground)
                        Spacer()
                        PapyButton(String(localized: "documents_clear"), style: .text, size: .small, fullWidth: false) {
                            model.viewModel.clearRecent()
                        }
                    }
                    .padding(.top, PapySpacing.lg)
                    ForEach(model.state.recent, id: \.ref) { item in
                        RecentRow(item: item, onOpen: { model.viewModel.recentClicked(item: item) }) {
                            model.viewModel.removeRecent(item: item)
                        }
                    }
                }
                PapyButton(String(localized: "licenses_title"), style: .text, size: .small, fullWidth: false, action: onLicenses)
                    .padding(.top, PapySpacing.xl)
                    .accessibilityIdentifier("documents_licenses")
            }
            .padding(PapySize.screenPadding)
        }
        .papyScreenBackground(grouped: true)
        .fileImporter(isPresented: $picking, allowedContentTypes: Self.types) { result in
            guard case .success(let url) = result, let ref = PapyDocumentEngine.reference(for: url) else { return }
            model.viewModel.fileChosen(ref: ref, name: url.lastPathComponent)
        }
        .task { await observeEvents() }
        .toolbar(.hidden, for: .navigationBar)
    }

    private func observeEvents() async {
        for await event in model.viewModel.events {
            switch onEnum(of: event) {
            case .open(let open):
                onOpen(open.ref, open.name)
            }
        }
    }
}

private struct RecentRow: View {
    let item: RecentDocument
    let onOpen: () -> Void
    let onRemove: () -> Void

    @Environment(\.locale) private var locale

    var body: some View {
        HStack(spacing: PapySpacing.sm) {
            Button(action: onOpen) {
                VStack(alignment: .leading, spacing: PapySpacing.xxs) {
                    Text(item.name)
                        .papyFont(PapyFont.subtitle)
                        .foregroundStyle(PapyColor.onSurface)
                        .lineLimit(2)
                    Text("\(item.kind.label) · \(Date(timeIntervalSince1970: TimeInterval(item.openedAt) / 1000).formatted(date: .abbreviated, time: .shortened))")
                        .papyFont(PapyFont.caption)
                        .foregroundStyle(PapyColor.onSurfaceVariant)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            Button(action: onRemove) {
                PapyIcon("papy_ic_close", size: PapySize.icon)
                    .foregroundStyle(PapyColor.onSurfaceVariant)
            }
            .accessibilityLabel(String(localized: "documents_remove"))
        }
        .padding(PapySpacing.lg)
        .background(PapyColor.surface, in: RoundedRectangle(cornerRadius: PapyRadius.xl, style: .continuous))
    }
}

extension DocumentKind {
    var label: String {
        switch self {
        case .pdf: "PDF"
        case .hwp: "HWP"
        case .hwpx: "HWPX"
        case .docx: "DOCX"
        }
    }
}
