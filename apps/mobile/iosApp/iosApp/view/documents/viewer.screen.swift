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
    /// 숨긴 입력칸이 조합 중인 글자(쪽 위에 띄워 보인다)
    @State private var composing = ""
    /// 입력칸에 조합 중이던 글자를 마저 넣으라는 신호(다른 곳을 누르기 전)
    @State private var flushToken = 0
    /// 공유·저장 창으로 넘길 PDF
    @State private var sharedPdf: URL?
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
                        highlight: state.selectionBoxes + (state.paragraph?.boxes ?? []),
                        caret: state.editing ? state.caret?.box : nil,
                        composing: composing,
                        gestures: state.editing ? PageGestures(
                            tap: { page, x, y in flush(); model.viewModel.tap(page: Int32(page), x: x, y: y) },
                            doubleTap: { page, x, y in flush(); model.viewModel.doubleTap(page: Int32(page), x: x, y: y) },
                            longPress: { page, x, y in flush(); model.viewModel.longPress(page: Int32(page), x: x, y: y) }
                        ) : nil,
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
            if state.phase == .ready && state.editing && state.caret != nil {
                // 키보드 글자를 받는 숨긴 입력칸
                KeyInput(
                    flushToken: flushToken,
                    onType: { model.viewModel.type(text: $0) },
                    onBackspace: { model.viewModel.backspace() },
                    onMove: { model.viewModel.moveCaret(move: $0) },
                    composing: $composing
                )
                .frame(width: 1, height: 1)
                .opacity(0.01)
            }
            if state.phase == .ready && state.editing && state.caret == nil && state.paragraph == nil {
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
        .safeAreaInset(edge: .bottom) {
            if state.phase == .ready && state.editing && state.caret != nil {
                FormatBar(state: state, viewModel: model.viewModel, flush: flush)
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
                    if state.canExportPdf && !state.editing {
                        Button { exportPdf() } label: { Image(systemName: "square.and.arrow.up") }
                            .disabled(state.exporting)
                            .accessibilityLabel(String(localized: "viewer_export_pdf"))
                    }
                    Button { model.viewModel.showText() } label: { PapyIcon("papy_ic_copy", size: PapySize.icon) }
                        .accessibilityLabel(String(localized: "viewer_text"))
                }
            }
        }
        .sheet(isPresented: Binding(get: { state.paragraph != nil }, set: { if !$0 { model.viewModel.cancelParagraph() } })) {
            editSheet
        }
        .sheet(isPresented: Binding(get: { sharedPdf != nil }, set: { if !$0 { sharedPdf = nil } })) {
            if let sharedPdf { ShareSheet(url: sharedPdf) }
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
            // 커서를 놓고 치고 굵게 → 문단 고치기 → 저장
            model.viewModel.tap(page: 0, x: 90, y: 75)
            for _ in 0..<50 where model.state.caret == nil { try? await Task.sleep(for: .milliseconds(100)) }
            model.viewModel.type(text: "CURSOR ")
            try? await Task.sleep(for: .milliseconds(500))
            model.viewModel.toggleStyle(toggle: .bold)
            try? await Task.sleep(for: .milliseconds(500))
            model.viewModel.longPress(page: 0, x: 90, y: 75)
            for _ in 0..<50 where model.state.paragraph == nil { try? await Task.sleep(for: .milliseconds(100)) }
            guard let hit = model.state.paragraph else { return }
            try? await Task.sleep(for: .seconds(1))
            model.viewModel.applyParagraph(text: hit.text + " EDITED ON IOS")
            try? await Task.sleep(for: .seconds(1))
            model.viewModel.save()
            try? await Task.sleep(for: .seconds(1))
            if ProcessInfo.processInfo.arguments.contains("-papyExportSmoke") {
                model.viewModel.toggleEditing()
                exportPdf()
            }
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
            case .exported(let exported):
                sharedPdf = URL(fileURLWithPath: exported.target)
            case .exportFailed:
                toast = PapyToast(message: String(localized: "viewer_export_failed"), type: .error)
            case .exit:
                dismiss()
            }
        }
    }

    /// 입력칸이 조합 중이던 글자를 마저 넣게 한다
    private func flush() {
        flushToken += 1
    }

    /// PDF 를 임시 폴더에 만들고(끝나면 공유·저장 창이 열린다)
    private func exportPdf() {
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent("Export", isDirectory: true)
        try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        // 문서 이름에 경로 글자가 있어도 폴더 밖으로 나가지 않게
        let name = state.pdfName.replacingOccurrences(of: "/", with: "_")
        model.viewModel.exportPdf(target: folder.appendingPathComponent(name).path)
    }

    private var editSheet: some View {
        NavigationStack {
            VStack(spacing: PapySpacing.md) {
                TextEditor(text: $draft)
                    .papyFont(PapyFont.body)
                    .padding(PapySpacing.sm)
                    .background(PapyColor.surfaceVariant, in: RoundedRectangle(cornerRadius: PapyRadius.lg))
                    .frame(minHeight: 120)
                PapyButton(String(localized: "viewer_edit_apply")) { model.viewModel.applyParagraph(text: draft) }
            }
            .padding(PapySize.screenPadding)
            .navigationTitle(String(localized: "viewer_edit_title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(String(localized: "common_cancel")) { model.viewModel.cancelParagraph() }
                }
            }
        }
        .presentationDetents([.medium, .large])
        .onAppear { draft = state.paragraph?.text ?? "" }
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
    let caret: PageBox?
    let composing: String
    /// 편집 모드에서 쪽을 누르는 동작(쪽, x pt, y pt)
    let gestures: PageGestures?
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
                            caret: caret.flatMap { Int($0.page) == index ? $0 : nil },
                            composing: composing,
                            gestures: gestures,
                            pageGestureIndex: index
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
            // 두 번 눌러 확대(편집 모드에서는 단어 고르기라 쓰지 않는다)
            .onTapGesture(count: 2) { if gestures == nil { withAnimation { zoom = zoom > 1 ? 1 : 2 } } }
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
    let caret: PageBox?
    let composing: String
    let gestures: PageGestures?
    let pageGestureIndex: Int

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
                if let caret {
                    Rectangle()
                        .fill(Color.accentColor)
                        .frame(width: 2, height: max(CGFloat(caret.height), 4) * scale)
                        .position(x: CGFloat(caret.x) * scale, y: (CGFloat(caret.y) + CGFloat(caret.height) / 2) * scale)
                    if !composing.isEmpty {
                        // 입력기가 조합 중인 글자: 커서 아래에 띄워 보인다
                        Text(composing)
                            .papyFont(PapyFont.body)
                            .foregroundStyle(.black)
                            .padding(.horizontal, 2)
                            .background(Color(red: 1, green: 0.95, blue: 0.69))
                            .border(Color.accentColor)
                            .fixedSize()
                            .position(x: CGFloat(caret.x) * scale + 20, y: (CGFloat(caret.y) + CGFloat(caret.height)) * scale + 12)
                    }
                }
                if let gestures {
                    // 화면 좌표 → 쪽 좌표(pt)
                    let pt = { (location: CGPoint) in (Float(location.x / scale), Float(location.y / scale)) }
                    let index = pageGestureIndex
                    Color.clear
                        .contentShape(Rectangle())
                        .gesture(
                            SpatialTapGesture(count: 2).onEnded { value in
                                let (x, y) = pt(value.location)
                                gestures.doubleTap(index, x, y)
                            }
                            .exclusively(before: SpatialTapGesture(count: 1).onEnded { value in
                                let (x, y) = pt(value.location)
                                gestures.tap(index, x, y)
                            })
                        )
                        // 길게 누른 뒤 손을 뗀 자리(길게 누르기는 자리를 알려 주지 않아 이어지는 끌기로 잰다)
                        .simultaneousGesture(
                            LongPressGesture(minimumDuration: 0.5)
                                .sequenced(before: DragGesture(minimumDistance: 0, coordinateSpace: .local))
                                .onEnded { value in
                                    if case .second(true, let drag?) = value {
                                        let (x, y) = pt(drag.location)
                                        gestures.longPress(index, x, y)
                                    }
                                }
                        )
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

/// 편집 모드에서 쪽을 누르는 동작(쪽, pt 좌표)
struct PageGestures {
    let tap: (Int, Float, Float) -> Void
    let doubleTap: (Int, Float, Float) -> Void
    let longPress: (Int, Float, Float) -> Void
}

/// 편집 도구 막대: 글자 모양·크기·색, 범위 선택, 커서 이동, 문단 고치기
private struct FormatBar: View {
    let state: ViewerUiState
    let viewModel: ViewerViewModel
    let flush: () -> Void

    private static let colors: [(String, Color, String)] = [
        ("#000000", .black, "viewer_color_black"),
        ("#e53935", Color(red: 0.9, green: 0.22, blue: 0.21), "viewer_color_red"),
        ("#1e63d6", Color(red: 0.12, green: 0.39, blue: 0.84), "viewer_color_blue"),
    ]

    var body: some View {
        let style = state.style
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: PapySpacing.xs) {
                tool("bold", "viewer_style_bold", on: style?.bold?.boolValue == true) { viewModel.toggleStyle(toggle: .bold) }
                tool("italic", "viewer_style_italic", on: style?.italic?.boolValue == true) { viewModel.toggleStyle(toggle: .italic) }
                tool("underline", "viewer_style_underline", on: style?.underline?.boolValue == true) { viewModel.toggleStyle(toggle: .underline) }
                tool("strikethrough", "viewer_style_strike", on: style?.strikethrough?.boolValue == true) { viewModel.toggleStyle(toggle: .strikethrough) }
                tool("textformat.size.smaller", "viewer_font_smaller") { viewModel.changeFontSize(steps: -1) }
                Text(style?.fontSize.map { String(format: "%.1f", $0.floatValue) } ?? "")
                    .papyFont(PapyFont.caption)
                    .foregroundStyle(PapyColor.onSurfaceVariant)
                tool("textformat.size.larger", "viewer_font_larger") { viewModel.changeFontSize(steps: 1) }
                ForEach(Self.colors, id: \.0) { hex, color, label in
                    Button { flush(); viewModel.setColor(color: hex) } label: {
                        Circle().fill(color).frame(width: 24, height: 24)
                            .overlay(Circle().stroke(style?.color?.lowercased() == hex ? Color.accentColor : .clear, lineWidth: 2))
                    }
                    .accessibilityLabel(String(localized: String.LocalizationValue(label)))
                }
                tool("arrow.left.and.right.text.vertical", "viewer_select_extend", on: state.extending) { viewModel.toggleExtending() }
                if state.selection != nil {
                    tool("xmark", "viewer_select_clear") { viewModel.collapseSelection() }
                }
                tool("chevron.left", "viewer_caret_left") { viewModel.moveCaret(move: .left) }
                tool("chevron.right", "viewer_caret_right") { viewModel.moveCaret(move: .right) }
                tool("paragraphsign", "viewer_edit_paragraph") {
                    if let box = state.caret?.box {
                        viewModel.longPress(page: box.page, x: box.x, y: box.y + box.height / 2)
                    }
                }
            }
            .padding(.horizontal, PapySpacing.sm)
            .padding(.vertical, PapySpacing.xs)
        }
        .background(PapyColor.surface)
    }

    private func tool(_ symbol: String, _ label: String, on: Bool = false, action: @escaping () -> Void) -> some View {
        Button { flush(); action() } label: {
            Image(systemName: symbol)
                .frame(width: 36, height: 36)
                .foregroundStyle(on ? Color.accentColor : PapyColor.onSurface)
                .background(on ? Color.accentColor.opacity(0.15) : .clear, in: RoundedRectangle(cornerRadius: 8))
        }
        .accessibilityLabel(String(localized: String.LocalizationValue(label)))
    }
}

/// 숨긴 입력칸: 키보드 글자를 받아 문서에 넣는다. 맨 앞 글자(SENTINEL)가 지워지면 "앞 글자 지우기"로 본다
/// (빈 칸에서는 지우기가 오지 않는다). 입력기가 조합 중인 글자(한글 등)는 조합이 끝난 뒤 넣는다.
private struct KeyInput: UIViewRepresentable {
    static let sentinel = "\u{200B}"

    let flushToken: Int
    let onType: (String) -> Void
    let onBackspace: () -> Void
    let onMove: (CaretMove) -> Void
    @Binding var composing: String

    func makeUIView(context: Context) -> Field {
        let field = Field()
        field.text = Self.sentinel
        field.autocorrectionType = .no
        field.spellCheckingType = .no
        field.autocapitalizationType = .none
        field.delegate = context.coordinator
        field.addTarget(context.coordinator, action: #selector(Coordinator.changed(_:)), for: .editingChanged)
        field.onMove = onMove
        DispatchQueue.main.async { field.becomeFirstResponder() }
        return field
    }

    func updateUIView(_ field: Field, context: Context) {
        context.coordinator.parent = self
        field.onMove = onMove
        if context.coordinator.flushToken != flushToken {
            context.coordinator.flushToken = flushToken
            // 화면을 그리는 중에 상태를 바꾸지 않게 다음 차례에
            DispatchQueue.main.async { context.coordinator.flush(field) }
        }
    }

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    final class Field: UITextField {
        var onMove: ((CaretMove) -> Void)?

        // 하드웨어 키보드 화살표
        override var keyCommands: [UIKeyCommand]? {
            [
                (UIKeyCommand.inputLeftArrow, CaretMove.left),
                (UIKeyCommand.inputRightArrow, CaretMove.right),
                (UIKeyCommand.inputUpArrow, CaretMove.up),
                (UIKeyCommand.inputDownArrow, CaretMove.down),
            ].map { input, _ in
                let command = UIKeyCommand(input: input, modifierFlags: [], action: #selector(arrow(_:)))
                command.wantsPriorityOverSystemBehavior = true
                return command
            }
        }

        @objc private func arrow(_ command: UIKeyCommand) {
            switch command.input {
            case UIKeyCommand.inputLeftArrow: onMove?(.left)
            case UIKeyCommand.inputRightArrow: onMove?(.right)
            case UIKeyCommand.inputUpArrow: onMove?(.up)
            default: onMove?(.down)
            }
        }
    }

    final class Coordinator: NSObject, UITextFieldDelegate {
        var parent: KeyInput
        var flushToken: Int
        /// 이미 문서에 넣은 글자(입력칸의 SENTINEL 뒤)
        private var sent = ""

        init(_ parent: KeyInput) {
            self.parent = parent
            flushToken = parent.flushToken
        }

        @objc func changed(_ field: UITextField) {
            let text = field.text ?? ""
            guard text.hasPrefix(KeyInput.sentinel) else {
                // 맨 앞 글자까지 지워졌다 → 넣은 글자 + 그 앞 한 글자를 지운다
                for _ in 0...sent.count { parent.onBackspace() }
                let rest = text.replacingOccurrences(of: KeyInput.sentinel, with: "")
                if !rest.isEmpty { parent.onType(rest) }
                reset(field)
                return
            }
            let all = String(text.dropFirst(KeyInput.sentinel.count))
            // 조합 중인 글자(markedText)는 아직 넣지 않는다
            var stable = all
            var marked = ""
            if let range = field.markedTextRange {
                let start = field.offset(from: field.beginningOfDocument, to: range.start) - KeyInput.sentinel.utf16.count
                let utf16 = Array(all.utf16)
                let cut = max(0, min(start, utf16.count))
                stable = String(decoding: utf16[..<cut], as: UTF16.self)
                marked = String(decoding: utf16[cut...], as: UTF16.self)
            }
            let common = sent.commonPrefix(with: stable).count
            for _ in 0..<(sent.count - common) { parent.onBackspace() }
            let added = String(stable.dropFirst(common))
            if !added.isEmpty { parent.onType(added) }
            sent = stable
            parent.composing = marked
            // 조합이 없을 때 가끔 비운다(입력칸이 한없이 길어지지 않게)
            if field.markedTextRange == nil && sent.count > 64 { reset(field) }
        }

        /// 조합 중이던 글자를 마저 넣고 비운다
        func flush(_ field: UITextField) {
            let pending = parent.composing
            // 글자를 다시 채우면 조합도 끝난다(이때는 editingChanged 가 오지 않는다)
            reset(field)
            if !pending.isEmpty { parent.onType(pending) }
        }

        private func reset(_ field: UITextField) {
            field.text = KeyInput.sentinel
            sent = ""
            parent.composing = ""
        }

        func textFieldShouldReturn(_ textField: UITextField) -> Bool {
            flush(textField)
            parent.onType("\n")
            return false
        }
    }
}

/// 공유·저장 창(파일에 저장, 다른 앱으로 보내기)
private struct ShareSheet: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: [url], applicationActivities: nil)
    }

    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}
