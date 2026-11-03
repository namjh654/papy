import PhotosUI
import SwiftUI
import Core

/// 글쓰기·고치기(20-4): 여러 줄 입력(글자 수 n/2000), 사진 줄(최대 4장, 빼기), 사진 추가는 사진·카메라 메뉴, 하단 고정 올리기/저장. (Android `PostEditorScreen`)
struct PostEditorScreen: View {

    let onSaved: () -> Void

    @StateObject private var model: SharedViewModel<PostEditorViewModel, PostEditorUiState>

    /// @param postId 고칠 글, 새 글은 nil
    init(postId: String?, onSaved: @escaping () -> Void) {
        self.onSaved = onSaved
        _model = StateObject(wrappedValue: SharedViewModel(IOSAppGraph.shared.postEditorViewModel(postId: postId), state: \.uiState))
    }

    @State private var toast: PapyToast?
    /// 새로 고른 사진의 기기 미리보기(key → 사진)
    @State private var previews: [String: UIImage] = [:]
    @State private var showAdd = false
    @State private var showPhotos = false
    @State private var photoSelection: [PhotosPickerItem] = []
    @State private var showCamera = false

    private var viewModel: PostEditorViewModel { model.viewModel }
    private var state: PostEditorUiState { model.state }
    private var images: [PostImage] { state.images.compactMap { $0 as? PostImage } }

    var body: some View {
        PapyFormScrollView {
            VStack(alignment: .trailing, spacing: PapySpacing.xs) {
                TextField(
                    String(localized: "community_content_placeholder"),
                    text: Binding(get: { state.content }, set: { viewModel.contentChanged(value: $0) }),
                    axis: .vertical
                )
                .papyFont(PapyFont.body)
                .lineLimit(8...)
                .padding(PapySpacing.lg)
                .frame(minHeight: 200, alignment: .topLeading)
                .background(PapyColor.fill, in: RoundedRectangle(cornerRadius: PapyRadius.lg, style: .continuous))
                Text(verbatim: "\(state.content.count)/\(state.maxContent)")
                    .papyFont(PapyFont.caption)
                    .foregroundStyle(state.content.count > Int(state.maxContent) ? PapyColor.error : PapyColor.onSurfaceTertiary)
            }

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: PapySpacing.sm) {
                    ForEach(Array(images.enumerated()), id: \.offset) { index, image in
                        thumbnail(image) { viewModel.removeImage(index: Int32(index)) }
                    }
                    if state.canAddImage {
                        Button { showAdd = true } label: {
                            VStack(spacing: PapySpacing.xxs) {
                                PapyIcon("papy_ic_image")
                                Text(verbatim: "\(images.count)/\(state.maxImages)").papyFont(PapyFont.caption)
                            }
                            .foregroundStyle(PapyColor.onSurfaceVariant)
                            .frame(width: PapySize.imageThumbnail, height: PapySize.imageThumbnail)
                            .background(PapyColor.fill, in: RoundedRectangle(cornerRadius: PapyRadius.md, style: .continuous))
                        }
                        .accessibilityLabel(String(localized: "community_add_photo"))
                    }
                }
            }

            if let error = state.error {
                PapyInlineMessage(message: error.localizedMessage, type: .error)
            }
        } bottomBar: {
            PapyButton(
                String(localized: state.isEditing ? "community_save" : "community_post"),
                enabled: state.canSave || state.isSaving,
                isLoading: state.isSaving
            ) { viewModel.save() }
        }
        .papyScreenBackground()
        .navigationTitle(String(localized: state.isEditing ? "community_edit_title" : "community_new_title"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
        .papyToast($toast)
        .papyActionSheet(isPresented: $showAdd, title: String(localized: "community_add_photo"), actions: [
            PapyAction(label: String(localized: "attach_photo"), icon: "papy_ic_image") { showPhotos = true },
            PapyAction(label: String(localized: "attach_camera"), icon: "papy_ic_camera") { openCamera() },
        ])
        .photosPicker(
            isPresented: $showPhotos,
            selection: $photoSelection,
            maxSelectionCount: max(1, Int(state.maxImages) - images.count),
            matching: .images
        )
        .onChange(of: photoSelection) { _, items in
            guard !items.isEmpty else { return }
            photoSelection = []
            Task { for item in items { await loadPhoto(item) } }
        }
        .fullScreenCover(isPresented: $showCamera) {
            CameraPicker { image in add { try Attachments.image(image) } }
                .ignoresSafeArea()
        }
        .task {
            await observeEvents()
        }
    }

    private func thumbnail(_ image: PostImage, onRemove: @escaping () -> Void) -> some View {
        ZStack(alignment: .topTrailing) {
            Group {
                switch onEnum(of: image) {
                case .local(let local):
                    if let preview = previews[local.key] { Image(uiImage: preview).resizable().scaledToFill() } else { PapyColor.fill }
                case .remote(let remote):
                    PapyPostImage(url: remote.url)
                }
            }
            .frame(width: PapySize.imageThumbnail, height: PapySize.imageThumbnail)
            .clipShape(RoundedRectangle(cornerRadius: PapyRadius.md, style: .continuous))
            Button(action: onRemove) {
                PapyIcon("papy_ic_close", size: 14)
                    .foregroundStyle(PapyColor.onPrimary)
                    .frame(width: 24, height: 24)
                    .background(PapyColor.scrim, in: Circle())
                    // 보이는 원은 24, 누르는 영역은 40(Android 와 같다)
                    .frame(width: 40, height: 40)
                    .contentShape(Rectangle())
            }
            .accessibilityLabel(String(localized: "community_remove_photo"))
        }
    }

    private func openCamera() {
        Task {
            do {
                try await Attachments.cameraAccess()
                showCamera = true
            } catch {
                toast = PapyToast(message: AttachmentFailure.from(error).message, type: .error)
            }
        }
    }

    private func loadPhoto(_ item: PhotosPickerItem) async {
        guard let data = try? await item.loadTransferable(type: Data.self), let image = UIImage(data: data) else {
            toast = PapyToast(message: AttachmentFailure.unreadable.message, type: .error)
            return
        }
        add { try Attachments.image(image) }
    }

    /// 사진을 KMP 에 넣고 미리보기를 key 로 들고 있는다. 4장이 넘으면 안내
    private func add(_ make: () throws -> (input: AttachmentInput, preview: UIImage)) {
        do {
            let picked = try make()
            if let key = viewModel.addImage(input: picked.input) {
                previews[key] = picked.preview
            } else {
                toast = PapyToast(message: String(localized: "error_too_many_images"), type: .error)
            }
        } catch {
            toast = PapyToast(message: AttachmentFailure.from(error).message, type: .error)
        }
    }
}


// MARK: - Event Observation

private extension PostEditorScreen {

    func observeEvents() async {
        for await event in viewModel.events {
            switch onEnum(of: event) {
            case .saved:
                onSaved()
            }
        }
    }
}
