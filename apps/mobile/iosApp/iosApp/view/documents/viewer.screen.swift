import SwiftUI
import UIKit
import Core

/// 문서 보기: 쪽을 세로로 이어 보이고, 두 손가락으로 확대한다. (Android `ViewerScreen`)
struct ViewerScreen: View {

    let name: String

    @StateObject private var model: SharedViewModel<ViewerViewModel, ViewerUiState>
    @State private var password = ""
    @State private var copied = false
    @State private var draft = ""
    @State private var toast: PapyToast?
    @Environment(\.dismiss) private var dismiss

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
                        revision: Int(state.revision),
                        highlight: state.selection?.boxes ?? [],
                        onTap: state.editing ? { page, x, y in
                            model.viewModel.pageTapped(page: Int32(page), x: x, y: y)
                        } : nil,
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
            if state.phase == .ready && state.editing && state.selection == nil {
                Text(String(localized: "viewer_edit_hint"))
                    .papyFont(PapyFont.bodySmall)
                    .foregroundStyle(PapyColor.onPrimary)
                    .padding(.horizontal, PapySpacing.md)
                    .padding(.vertical, PapySpacing.xs)
                    .background(PapyColor.primary, in: Capsule())
                    .padding(PapySpacing.sm)
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
        // 저장하지 않은 변경이 있으면 뒤로 가기 전에 묻는다
        .navigationBarBackButtonHidden(state.dirty)
        .toolbar {
            if state.dirty {
                ToolbarItem(placement: .topBarLeading) {
                    Button { model.viewModel.requestExit() } label: { Image(systemName: "chevron.backward") }
                        .accessibilityLabel(String(localized: "common_back"))
                }
            }
            if state.phase == .ready {
                ToolbarItemGroup(placement: .topBarTrailing) {
                    if state.editing && state.canUndo {
                        Button { model.viewModel.undo() } label: { Image(systemName: "arrow.uturn.backward") }
                            .accessibilityLabel(String(localized: "viewer_undo"))
                    }
                    if state.dirty {
                        Button(String(localized: "viewer_save")) { model.viewModel.save() }
                            .bold()
                            .disabled(state.saving)
                    }
                    if state.canEdit {
                        Button { model.viewModel.toggleEditing() } label: {
                            Image(systemName: state.editing ? "checkmark" : "pencil")
                        }
                        .accessibilityLabel(String(localized: state.editing ? "viewer_edit_done" : "viewer_edit"))
                    }
                    Button { model.viewModel.showText() } label: { PapyIcon("papy_ic_copy", size: PapySize.icon) }
                        .accessibilityLabel(String(localized: "viewer_text"))
                }
            }
        }
        .sheet(isPresented: Binding(get: { state.selection != nil }, set: { if !$0 { model.viewModel.cancelSelection() } })) {
            editSheet
        }
        .alert(
            String(localized: "viewer_exit_title"),
            isPresented: Binding(get: { state.confirmExit }, set: { if !$0 { model.viewModel.cancelExit() } })
        ) {
            Button(String(localized: "viewer_save")) { model.viewModel.save() }
            Button(String(localized: "viewer_exit_discard"), role: .destructive) { model.viewModel.discardAndExit() }
            Button(String(localized: "common_cancel"), role: .cancel) { model.viewModel.cancelExit() }
        } message: {
            Text(String(localized: "viewer_exit_message"))
        }
        .papyToast($toast)
        .task { await observeEvents() }
        #if DEBUG
        // 개발 확인용: `-papyEditSmoke` 이면 화면 동작과 같은 순서로 편집 → 첫 문단 누르기 → 고치기 → 저장(시뮬레이터 점검)
        .task(id: state.phase) {
            guard ProcessInfo.processInfo.arguments.contains("-papyEditSmoke"), state.phase == .ready, state.canEdit else { return }
            model.viewModel.toggleEditing()
            model.viewModel.pageTapped(page: 0, x: 90, y: 75)
            for _ in 0..<50 where model.state.selection == nil { try? await Task.sleep(for: .milliseconds(100)) }
            guard let hit = model.state.selection else { return }
            try? await Task.sleep(for: .seconds(1))
            model.viewModel.applyEdit(text: hit.text + " EDITED ON IOS")
            try? await Task.sleep(for: .seconds(1))
            model.viewModel.save()
        }
        #endif
        .sheet(isPresented: Binding(get: { state.text != nil }, set: { if !$0 { model.viewModel.hideText() } })) {
            textSheet
        }
    }

    private func observeEvents() async {
        for await event in model.viewModel.events {
            switch onEnum(of: event) {
            case .saved:
                toast = PapyToast(message: String(localized: "viewer_saved"), type: .success)
            case .saveFailed(let failed):
                toast = PapyToast(
                    message: String(localized: failed.error == .readOnly ? "viewer_save_readonly" : "viewer_save_failed"),
                    type: .error
                )
            case .editFailed:
                toast = PapyToast(message: String(localized: "viewer_edit_failed"), type: .error)
            case .exit:
                dismiss()
            }
        }
    }

    private var editSheet: some View {
        NavigationStack {
            VStack(spacing: PapySpacing.md) {
                TextEditor(text: $draft)
                    .papyFont(PapyFont.body)
                    .padding(PapySpacing.sm)
                    .background(PapyColor.surfaceVariant, in: RoundedRectangle(cornerRadius: PapyRadius.lg))
                    .frame(minHeight: 120)
                PapyButton(String(localized: "viewer_edit_apply")) { model.viewModel.applyEdit(text: draft) }
            }
            .padding(PapySize.screenPadding)
            .navigationTitle(String(localized: "viewer_edit_title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(String(localized: "common_cancel")) { model.viewModel.cancelSelection() }
                }
            }
        }
        .presentationDetents([.medium, .large])
        .onAppear { draft = state.selection?.text ?? "" }
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

/// 쪽 목록. 확대하는 동안은 그림을 늘려 보이고, 손을 떼면 확대 배율에 맞춰 보이는 쪽을 다시 그린다(쪽당 최대 1200만 픽셀).
private struct PagesView: View {
    let session: Int64
    let sizes: [PageSizePt]
    let revision: Int
    let highlight: [PageBox]
    /// 편집 모드에서 쪽을 눌렀다(쪽, x pt, y pt)
    let onTap: ((Int, Float, Float) -> Void)?
    let onPageVisible: (Int) -> Void

    @State private var zoom: CGFloat = 1
    @GestureState private var pinch: CGFloat = 1

    /// 픽셀 상한(약 48MB, RGBA) 안의 너비
    static func width(_ wanted: CGFloat, _ size: PageSizePt) -> CGFloat {
        let aspect = min(max(CGFloat(size.height) / max(CGFloat(size.width), 1), 0.1), 10)
        return max(1, min(wanted, (12_000_000 / aspect).squareRoot())).rounded()
    }

    var body: some View {
        GeometryReader { geometry in
            let renderWidth = geometry.size.width * UIScreen.main.scale * 1.25 * zoom
            ScrollView([.vertical, .horizontal]) {
                LazyVStack(spacing: PapySpacing.sm) {
                    ForEach(Array(sizes.enumerated()), id: \.offset) { index, size in
                        PageView(
                            session: session, index: index, size: size, renderWidth: Self.width(renderWidth, size),
                            revision: revision, highlight: highlight.filter { Int($0.page) == index },
                            onTap: onTap.map { tap in { x, y in tap(index, x, y) } }
                        )
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
    let size: PageSizePt
    let renderWidth: CGFloat
    let revision: Int
    let highlight: [PageBox]
    let onTap: ((Float, Float) -> Void)?

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
        .overlay {
            GeometryReader { geometry in
                let scale = geometry.size.width / CGFloat(max(size.width, 1))
                ForEach(Array(highlight.enumerated()), id: \.offset) { _, box in
                    Rectangle()
                        .fill(Color.blue.opacity(0.3))
                        .frame(width: CGFloat(box.width) * scale, height: CGFloat(box.height) * scale)
                        .position(
                            x: (CGFloat(box.x) + CGFloat(box.width) / 2) * scale,
                            y: (CGFloat(box.y) + CGFloat(box.height) / 2) * scale
                        )
                }
                if let onTap {
                    // 화면 좌표 → 쪽 좌표(pt)
                    Color.clear
                        .contentShape(Rectangle())
                        .onTapGesture(coordinateSpace: .local) { location in
                            onTap(Float(location.x / scale), Float(location.y / scale))
                        }
                }
            }
        }
        .task(id: "\(renderWidth)-\(revision)") {
            let session = session, index = index, width = renderWidth
            let rendered = await Task.detached(priority: .userInitiated) {
                PapyDocumentEngine.shared.render(session: session, page: index, widthPx: width)
            }.value
            // 다시 그리는 동안 이전 그림을 그대로 두고, 실패하면 이전 그림을 남긴다
            if let rendered { image = rendered }
            failed = image == nil
        }
    }
}
