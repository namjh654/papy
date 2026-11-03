import PhotosUI
import QuickLook
import SwiftUI
import Core

/// 대화방(19-4): 아래에 붙어 쌓이는 말풍선, 맨 위에서 이전 메시지, 위로 스크롤하면 "맨 아래로" 버튼, 입력창은 키보드 위.
/// 첨부(사진·카메라·파일)는 액션 메뉴, 말풍선을 길게 누르면 답장·복사·다시 보내기·지우기. (Android `ChatRoomScreen`)
/// 22: 오른쪽으로 밀어서 답장, 인용을 누르면 원문으로(잠깐 강조), 사진을 누르면 전체 화면, 위 돋보기로 대화 검색, 상대 입력 중 점 3개.
struct ChatRoomScreen: View {

    @StateObject private var model: SharedViewModel<ChatRoomViewModel, ChatRoomUiState>

    /// - Parameter peerUserId: 프로필의 "메시지 보내기"(25-2) — 대화 id 없이 사람으로 연다(KMP 가 1:1 대화를 찾거나 만든다)
    init(conversationId: String, title: String, peerUserId: String? = nil) {
        let viewModel = if let peerUserId {
            IOSAppGraph.shared.directChatRoomViewModel(userId: peerUserId, name: title)
        } else {
            IOSAppGraph.shared.chatRoomViewModel(conversationId: conversationId, title: title)
        }
        _model = StateObject(wrappedValue: SharedViewModel(viewModel, state: \.uiState))
    }

    @Environment(\.locale) private var locale
    @State private var toast: ElsaToast?
    /// 보내는 중 사진의 기기 미리보기(말풍선 key → 사진)
    @State private var previews: [String: UIImage] = [:]
    @State private var showAttach = false
    @State private var showPhotoPicker = false
    @State private var photoSelection: PhotosPickerItem?
    @State private var showCamera = false
    @State private var showFiles = false
    @State private var menuFor: ChatMessage?
    /** 받아 둔 파일 미리 보기(QuickLook — PDF·이미지·문서, 공유도 여기서) */
    @State private var quickLook: URL?
    @State private var awayFromBottom = false
    /// KMP `ScrollToBottom` 이벤트마다 1 씩 — 목록(ScrollViewReader 안)이 보고 내린다
    @State private var scrollRequest = 0
    /// 검색 결과·인용으로 옮겨 갈 말풍선(강조도 이것)
    @State private var scrollTarget: String?
    @State private var highlightKey: String?
    @State private var viewer: ViewerImage?
    @State private var swipe: [String: CGFloat] = [:]
    @FocusState private var searchFocused: Bool

    private struct ViewerImage: Identifiable {
        let id = UUID()
        let preview: UIImage?
        let url: String?
    }

    private func replyName(_ isMine: Bool) -> String { isMine ? String(localized: "chat_reply_me") : state.title }

    private func previewText(_ text: String?, _ kind: AttachmentKind?) -> String {
        text ?? String(localized: kind == .image ? "chat_attachment_photo" : "chat_attachment_file")
    }

    private var viewModel: ChatRoomViewModel { model.viewModel }
    private var state: ChatRoomUiState { model.state }
    private var rows: [ChatRow] { state.rows.compactMap { $0 as? ChatRow } }

    var body: some View {
        content
            .safeAreaInset(edge: .bottom, spacing: 0) {
                if !state.isSearchOpen {
                    VStack(spacing: 0) {
                        if let target = state.replyingTo {
                            ElsaReplyBar(
                                name: replyName(target.isMine),
                                text: previewText(target.message.text, target.message.attachment?.kind),
                                onCancel: { viewModel.cancelReply() }
                            )
                        }
                        ElsaChatInputBar(
                            text: Binding(get: { state.input }, set: { viewModel.inputChanged(value: $0) }),
                            canSend: state.canSend,
                            onSend: { viewModel.send() },
                            // 서버가 첨부를 끄면 버튼이 없다(FeatureKey.chatAttachments)
                            onAttach: state.attachmentsEnabled ? { showAttach = true } : nil
                        )
                    }
                }
            }
            .overlay {
                if state.isSearchOpen { searchResults }
            }
            .toolbar {
                if state.isSearchOpen {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button(String(localized: "chat_close")) { viewModel.closeSearch() }
                    }
                } else {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button { viewModel.openSearch() } label: { ElsaIcon("elsa_ic_search") }
                            .accessibilityLabel(String(localized: "chat_search"))
                    }
                }
            }
            .fullScreenCover(item: $viewer) { image in
                ElsaImageViewer(preview: image.preview, url: image.url) { viewer = nil }
            }
            .elsaScreenBackground(grouped: true)
            .navigationTitle(state.isSearchOpen ? "" : state.title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar(.hidden, for: .tabBar)
            .elsaToast($toast)
            .quickLookPreview($quickLook)
            .elsaActionSheet(isPresented: $showAttach, title: String(localized: "attach_title"), actions: [
                ElsaAction(label: String(localized: "attach_photo"), icon: "elsa_ic_image") { showPhotoPicker = true },
                ElsaAction(label: String(localized: "attach_camera"), icon: "elsa_ic_camera") { openCamera() },
                ElsaAction(label: String(localized: "attach_file"), icon: "elsa_ic_file") { showFiles = true },
            ])
            .elsaActionSheet(
                isPresented: Binding(get: { menuFor != nil }, set: { if !$0 { menuFor = nil } }),
                actions: menuActions
            )
            .photosPicker(isPresented: $showPhotoPicker, selection: $photoSelection, matching: .images)
            .onChange(of: photoSelection) { _, item in
                guard let item else { return }
                photoSelection = nil
                Task { await loadPhoto(item) }
            }
            .fullScreenCover(isPresented: $showCamera) {
                CameraPicker { image in send { try Attachments.image(image) } }
                    .ignoresSafeArea()
            }
            .fileImporter(isPresented: $showFiles, allowedContentTypes: [.item]) { result in
                if case .success(let url) = result { send { try Attachments.file(url) } }
            }
            .task {
                await observeEvents()
            }
    }

    @ViewBuilder
    private var content: some View {
        if state.isLoading && rows.isEmpty {
            ElsaLoadingIndicator().frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if let error = state.error, rows.isEmpty {
            ElsaErrorState(message: error.localizedMessage, onRetry: { viewModel.load() })
                .frame(maxHeight: .infinity)
        } else if rows.isEmpty {
            ElsaEmptyState(title: String(localized: "chat_room_empty"))
                .frame(maxHeight: .infinity)
        } else {
            messageList
        }
    }

    private var messageList: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 0) {
                    if state.hasOlder {
                        ElsaLoadingIndicator()
                            .padding(ElsaSpacing.sm)
                            .onAppear { viewModel.loadOlder() }
                    }
                    ForEach(rows, id: \.key) { row in
                        rowView(row).id(row.key)
                    }
                    if state.isPeerTyping {
                        ElsaTypingIndicator().padding(.top, ElsaSpacing.sm).id("typing")
                    }
                }
                .padding(.horizontal, ElsaSpacing.md)
                .padding(.vertical, ElsaSpacing.sm)
            }
            // 처음·내용이 늘 때 아래에 붙는다(위에 이전 메시지가 붙어도 보던 자리가 그대로)
            .defaultScrollAnchor(.bottom)
            .scrollDismissesKeyboard(.interactively)
            .onScrollGeometryChange(for: Bool.self) { geometry in
                geometry.contentSize.height - geometry.visibleRect.maxY > 240
            } action: { _, away in
                withAnimation(.easeInOut(duration: 0.2)) { awayFromBottom = away }
            }
            .overlay(alignment: .bottomTrailing) {
                if awayFromBottom {
                    ElsaScrollToBottomButton { scrollToBottom(proxy) }
                        .padding(ElsaSpacing.lg)
                }
            }
            .onChange(of: scrollRequest) { scrollToBottom(proxy) }
            .onChange(of: scrollTarget) { _, key in
                guard let key else { return }
                withAnimation { proxy.scrollTo(key, anchor: .center) }
                highlightKey = key
                scrollTarget = nil
                Task {
                    try? await Task.sleep(for: .milliseconds(1_500))
                    withAnimation { highlightKey = nil }
                }
            }
        }
    }

    /// 파일 말풍선: 받는 중이면 진행, 받아 두었으면 "저장됨", 실패면 다시 누르라는 안내
    private func fileBubble(_ attachment: Attachment) -> some View {
        let size = ElsaFormat.fileSize(attachment.size, locale: locale)
        let fileState = state.fileStates[attachment.url]
        let caption: String
        if fileState is DownloadStateFailed {
            caption = String(localized: "file_download_failed_short")
        } else if state.downloadedUrls.contains(attachment.url) {
            caption = String(format: String(localized: "file_saved_caption"), size)
        } else {
            caption = size
        }
        let downloading = fileState as? DownloadStateDownloading
        return ElsaMessageFile(
            name: attachment.name,
            size: caption,
            downloading: downloading != nil,
            progress: downloading?.progress?.floatValue
        )
    }

    @ViewBuilder
    private func rowView(_ row: ChatRow) -> some View {
        switch onEnum(of: row) {
        case .dateHeader(let header):
            ElsaDateSeparator(text: ElsaFormat.dateLong(Date(timeIntervalSince1970: TimeInterval(header.dayStartMillis) / 1000), locale: locale))
        case .bubble(let bubble):
            messageRow(bubble)
        }
    }

    private func messageRow(_ row: ChatRowBubble) -> some View {
        let item = row.item
        let attachment = item.message.attachment
        let isImage = attachment?.kind == .image
        let startsGroup = row.position == .first || row.position == .single
        let reply = item.message.replyTo
        let canReply = item.sendState == .sent
        return ElsaMessageBubble(
            isMine: item.isMine,
            position: row.position,
            time: row.showTime ? ElsaFormat.time(epochMillis: item.message.sentAt, locale: locale) : nil,
            sendState: item.sendState,
            isRead: item.isRead,
            fillContent: isImage
        ) {
            VStack(alignment: .leading, spacing: ElsaSpacing.xs) {
                if let reply {
                    ElsaReplyQuote(name: replyName(item.replyIsMine), text: previewText(reply.text, reply.attachmentKind)) {
                        viewModel.replyQuoteClicked(messageId: reply.messageId)
                    }
                    .padding(isImage ? ElsaSpacing.xs : 0)
                }
                if let attachment {
                    if isImage {
                        ElsaMessageImage(url: attachment.url, preview: previews[item.key])
                    } else {
                        fileBubble(attachment)
                    }
                } else {
                    ElsaMessageText(text: item.message.text ?? "")
                }
            }
        }
        .background(
            highlightKey == item.key ? ElsaColor.primary.opacity(0.15) : Color.clear,
            in: RoundedRectangle(cornerRadius: ElsaRadius.md, style: .continuous)
        )
        .offset(x: swipe[item.key] ?? 0)
        .contentShape(Rectangle())
        .onTapGesture {
            if item.sendState == .failed {
                viewModel.retry(key: item.key)
            } else if let attachment, isImage {
                if previews[item.key] != nil || !attachment.url.isEmpty {
                    viewer = ViewerImage(preview: previews[item.key], url: attachment.url.isEmpty ? nil : attachment.url)
                }
            } else if attachment != nil {
                // 파일: 받아 두고 연다(30-1)
                viewModel.fileClicked(message: item.message)
            }
        }
        .onLongPressGesture { menuFor = item }
        // 오른쪽으로 64 넘게 밀었다 놓으면 답장(세로 스크롤과 겹치지 않게 가로로 더 많이 움직일 때만)
        .simultaneousGesture(
            DragGesture(minimumDistance: 20)
                .onChanged { value in
                    guard canReply, value.translation.width > abs(value.translation.height) else { return }
                    swipe[item.key] = min(max(0, value.translation.width), 84)
                }
                .onEnded { _ in
                    if (swipe[item.key] ?? 0) > 64 { viewModel.replyClicked(key: item.key) }
                    withAnimation(.spring(duration: 0.25)) { swipe[item.key] = 0 }
                }
        )
        .padding(.top, startsGroup ? ElsaSpacing.sm : ElsaSpacing.xxs)
    }

    private var menuActions: [ElsaAction] {
        guard let item = menuFor else { return [] }
        var actions: [ElsaAction] = []
        if item.sendState == .sent {
            actions.append(ElsaAction(label: String(localized: "chat_reply"), icon: "elsa_ic_reply") { viewModel.replyClicked(key: item.key) })
        }
        if let text = item.message.text {
            actions.append(ElsaAction(label: String(localized: "chat_copy"), icon: "elsa_ic_copy") {
                UIPasteboard.general.string = text
                toast = ElsaToast(message: String(localized: "chat_copied"), type: .success)
            })
        }
        if item.sendState == .failed {
            actions.append(ElsaAction(label: String(localized: "chat_retry"), icon: "elsa_ic_reply") { viewModel.retry(key: item.key) })
            actions.append(ElsaAction(label: String(localized: "chat_delete"), icon: "elsa_ic_delete", isDestructive: true) {
                viewModel.deleteFailed(key: item.key)
            })
        }
        return actions
    }

    // MARK: - Search

    /// 대화 검색 결과(22-4): 메시지 한 줄 + 날짜. 누르면 그 메시지로
    @ViewBuilder
    private var searchResults: some View {
        let results = state.searchResults.compactMap { $0 as? Message }
        VStack(spacing: 0) {
            // 검색창은 화면 폭으로(상단 바 가운데 칸은 좁다), 열면 바로 입력
            ElsaSearchBar(
                text: Binding(get: { state.searchQuery }, set: { viewModel.searchQueryChanged(value: $0) }),
                placeholder: String(localized: "chat_search_placeholder"),
                focus: $searchFocused,
                onSearch: {}
            )
            .onAppear { searchFocused = true }
            .padding(.horizontal, ElsaSize.screenPadding)
            .padding(.vertical, ElsaSpacing.sm)
            if state.isSearching && results.isEmpty {
                ElsaLoadingIndicator().frame(maxHeight: .infinity)
            } else if !state.searchQuery.isEmpty && results.isEmpty {
                ElsaEmptyState(title: String(localized: "chat_search_empty")).frame(maxHeight: .infinity, alignment: .top)
            } else {
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ForEach(results, id: \.id) { message in
                            ElsaListItem(
                                title: message.text ?? "",
                                subtitle: ElsaFormat.date(epochMillis: message.sentAt, locale: locale),
                                action: { viewModel.searchResultClicked(message: message) }
                            )
                        }
                    }
                }
            }
        }
        .background(ElsaColor.background)
    }

    // MARK: - Attachments

    private func openCamera() {
        Task {
            do {
                try await Attachments.cameraAccess()
                showCamera = true
            } catch {
                toast = ElsaToast(message: AttachmentFailure.from(error).message, type: .error)
            }
        }
    }

    private func loadPhoto(_ item: PhotosPickerItem) async {
        guard let data = try? await item.loadTransferable(type: Data.self), let image = UIImage(data: data) else {
            toast = ElsaToast(message: AttachmentFailure.unreadable.message, type: .error)
            return
        }
        send { try Attachments.image(image) }
    }

    /// 첨부를 만들어 KMP 로 보내고, 사진이면 미리보기를 key 로 들고 있는다
    private func send(_ make: () throws -> (input: AttachmentInput, preview: UIImage?)) {
        do {
            let picked = try make()
            let key = viewModel.sendAttachment(input: picked.input)
            if let preview = picked.preview { previews[key] = preview }
        } catch {
            toast = ElsaToast(message: AttachmentFailure.from(error).message, type: .error)
        }
    }

    // MARK: - Scroll

    private func scrollToBottom(_ proxy: ScrollViewProxy) {
        guard let last = rows.last?.key else { return }
        withAnimation(.easeOut(duration: 0.25)) { proxy.scrollTo(last, anchor: .bottom) }
    }
}

// MARK: - Event Observation

private extension ChatRoomScreen {

    func observeEvents() async {
        for await event in viewModel.events {
            switch onEnum(of: event) {
            case .scrollToBottom:
                // 새 말풍선이 그려진 다음에 내린다
                try? await Task.sleep(for: .milliseconds(50))
                scrollRequest += 1
            case .scrollToMessage(let target):
                try? await Task.sleep(for: .milliseconds(50))
                scrollTarget = target.key
            case .messageNotFound:
                toast = ElsaToast(message: String(localized: "chat_not_found"), type: .info)
            case .openFile(let open):
                quickLook = URL(fileURLWithPath: open.file.path)
            case .fileFailed(let failed):
                toast = ElsaToast(message: failed.error.localizedMessage, type: .error)
            }
        }
    }
}
