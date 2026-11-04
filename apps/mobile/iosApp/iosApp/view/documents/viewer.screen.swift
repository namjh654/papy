import SwiftUI
import UIKit
import Core

/// 문서 보기: 쪽을 세로로 이어 보이고, 두 손가락으로 확대한다. (Android `ViewerScreen`)
struct ViewerScreen: View {

    let name: String

    @StateObject private var model: SharedViewModel<ViewerViewModel, ViewerUiState>
    @State private var password = ""
    @State private var copied = false

    init(ref: String, name: String) {
        self.name = name
        _model = StateObject(wrappedValue: SharedViewModel(IOSAppGraph.shared.viewerViewModel(ref: ref, name: name), state: \.uiState))
    }

    private var state: ViewerUiState { model.state }

    var body: some View {
        ZStack(alignment: .top) {
            switch state.phase {
            case .opening:
                VStack(spacing: PapySpacing.md) {
                    PapyLoadingIndicator()
                    Text(String(localized: "viewer_opening"))
                        .papyFont(PapyFont.body)
                        .foregroundStyle(PapyColor.onSurfaceVariant)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            case .needsPassword:
                passwordForm
            case .failed:
                PapyErrorState(message: errorMessage) { model.viewModel.retry() }
                    .frame(maxHeight: .infinity)
            case .ready:
                if let document = state.document {
                    PagesView(
                        session: document.session,
                        sizes: state.pageSizes,
                        onPageVisible: { model.viewModel.pageVisible(index: Int32($0)) }
                    )
                    .overlay(alignment: .bottom) {
                        Text(String(format: String(localized: "viewer_page_indicator"), Int(state.currentPage) + 1, Int(state.pageCount)))
                            .papyFont(PapyFont.caption)
                            .foregroundStyle(PapyColor.surface)
                            .padding(.horizontal, PapySpacing.md)
                            .padding(.vertical, PapySpacing.xs)
                            .background(PapyColor.onSurface.opacity(0.8), in: Capsule())
                            .padding(PapySpacing.md)
                    }
                }
            }
            if state.phase == .ready && state.showLoss {
                HStack {
                    Text(String(format: String(localized: "viewer_loss"), Int(state.document?.droppedObjects ?? 0)))
                        .papyFont(PapyFont.bodySmall)
                        .foregroundStyle(PapyColor.surface)
                    Button { model.viewModel.dismissLoss() } label: {
                        PapyIcon("papy_ic_close", size: PapySize.icon).foregroundStyle(PapyColor.surface)
                    }
                    .accessibilityLabel(String(localized: "common_close"))
                }
                .padding(PapySpacing.md)
                .background(PapyColor.onSurface, in: RoundedRectangle(cornerRadius: PapyRadius.lg))
                .padding(PapySpacing.sm)
            }
        }
        .papyScreenBackground(grouped: true)
        .navigationTitle(name)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if state.phase == .ready {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { model.viewModel.showText() } label: { PapyIcon("papy_ic_copy", size: PapySize.icon) }
                        .accessibilityLabel(String(localized: "viewer_text"))
                }
            }
        }
        .sheet(isPresented: Binding(get: { state.text != nil }, set: { if !$0 { model.viewModel.hideText() } })) {
            textSheet
        }
    }

    private var errorMessage: String {
        switch state.error {
        case .unreadable: String(localized: "viewer_error_unreadable")
        case .unsupported, .passwordRequired: String(localized: "viewer_error_unsupported")
        case .invalid: String(localized: "viewer_error_invalid")
        case .tooLarge: String(localized: "viewer_error_too_large")
        default: String(localized: "viewer_error_internal")
        }
    }

    private var passwordForm: some View {
        VStack(alignment: .leading, spacing: PapySpacing.md) {
            PapyScreenHeader(
                title: String(localized: "viewer_password_title"),
                description: String(format: String(localized: "viewer_password_message"), state.name)
            )
            PapyTextField(
                nil,
                text: $password,
                errorText: state.wrongPassword ? String(localized: "viewer_password_wrong") : nil,
                isSecure: true
            )
            PapyButton(String(localized: "viewer_password_open"), enabled: !password.isEmpty) {
                model.viewModel.submitPassword(password: password)
            }
            Spacer()
        }
        .padding(PapySize.screenPadding)
    }

    @ViewBuilder
    private var textSheet: some View {
        if let text = state.text {
            NavigationStack {
                Group {
                    if text.text == nil {
                        PapyLoadingIndicator()
                    } else if text.unavailable {
                        Text(String(localized: "viewer_text_unavailable"))
                            .papyFont(PapyFont.body)
                            .foregroundStyle(PapyColor.onSurfaceVariant)
                            .padding()
                    } else {
                        ScrollView {
                            Text(text.text ?? "")
                                .papyFont(PapyFont.body)
                                .textSelection(.enabled)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding()
                        }
                        .safeAreaInset(edge: .bottom) {
                            PapyButton(String(localized: copied ? "viewer_text_copied" : "viewer_text_copy")) {
                                UIPasteboard.general.string = text.text
                                copied = true
                            }
                            .padding()
                        }
                    }
                }
                .navigationTitle(String(format: String(localized: "viewer_text_title"), Int(text.page) + 1))
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button(String(localized: "common_close")) { model.viewModel.hideText() }
                    }
                }
            }
            .presentationDetents([.medium, .large])
            .onDisappear { copied = false }
        }
    }
}

/// 쪽 목록. 확대는 화면에서 늘리고, 그림은 화면 너비 × 1.5 배(최대 2400px)로 한 번 그린다.
private struct PagesView: View {
    let session: Int64
    let sizes: [PageSizePt]
    let onPageVisible: (Int) -> Void

    @State private var zoom: CGFloat = 1
    @GestureState private var pinch: CGFloat = 1

    var body: some View {
        GeometryReader { geometry in
            let renderWidth = min(geometry.size.width * UIScreen.main.scale * 1.5, 2400)
            ScrollView([.vertical, .horizontal]) {
                LazyVStack(spacing: PapySpacing.sm) {
                    ForEach(Array(sizes.enumerated()), id: \.offset) { index, size in
                        PageView(session: session, index: index, renderWidth: renderWidth)
                            .aspectRatio(CGFloat(size.width / max(size.height, 1)), contentMode: .fit)
                            .frame(width: (geometry.size.width - PapySpacing.sm * 2) * zoom * pinch)
                            .onAppear { onPageVisible(index) }
                            .accessibilityLabel(String(format: String(localized: "viewer_page_indicator"), index + 1, sizes.count))
                    }
                }
                .padding(PapySpacing.sm)
            }
            .gesture(
                MagnifyGesture()
                    .updating($pinch) { value, state, _ in state = value.magnification }
                    .onEnded { value in zoom = min(max(zoom * value.magnification, 1), 4) }
            )
            .onTapGesture(count: 2) { withAnimation { zoom = zoom > 1 ? 1 : 2 } }
        }
    }
}

private struct PageView: View {
    let session: Int64
    let index: Int
    let renderWidth: CGFloat

    @State private var image: UIImage?
    @State private var failed = false

    var body: some View {
        ZStack {
            Color.white
            if let image {
                Image(uiImage: image).resizable().interpolation(.medium)
            } else if failed {
                Text(String(localized: "viewer_page_failed"))
                    .papyFont(PapyFont.caption)
                    .foregroundStyle(.gray)
            } else {
                PapyLoadingIndicator()
            }
        }
        .task(id: renderWidth) {
            let session = session, index = index, width = renderWidth
            let rendered = await Task.detached(priority: .userInitiated) {
                PapyDocumentEngine.shared.render(session: session, page: index, widthPx: width)
            }.value
            image = rendered
            failed = rendered == nil
        }
    }
}
