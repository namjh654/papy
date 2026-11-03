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

    @State private var toast: ElsaToast?
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
        ElsaFormScrollView {
            VStack(alignment: .trailing, spacing: ElsaSpacing.xs) {
                TextField(
                    String(localized: "community_content_placeholder"),
                    text: Binding(get: { state.content }, set: { viewModel.contentChanged(value: $0) }),
                    axis: .vertical
                )
                .elsaFont(ElsaFont.body)
                .lineLimit(8...)
                .padding(ElsaSpacing.lg)
                .frame(minHeight: 200, alignment: .topLeading)
                .background(ElsaColor.fill, in: RoundedRectangle(cornerRadius: ElsaRadius.lg, style: .continuous))
                Text(verbatim: "\(state.content.count)/\(state.maxContent)")
                    .elsaFont(ElsaFont.caption)
                    .foregroundStyle(state.content.count > Int(state.maxContent) ? ElsaColor.error : ElsaColor.onSurfaceTertiary)
            }

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: ElsaSpacing.sm) {
                    ForEach(Array(images.enumerated()), id: \.offset) { index, image in
                        thumbnail(image) { viewModel.removeImage(index: Int32(index)) }
                    }
                    if state.canAddImage {
                        Button { showAdd = true } label: {
                            VStack(spacing: ElsaSpacing.xxs) {
                                ElsaIcon("elsa_ic_image")
                                Text(verbatim: "\(images.count)/\(state.maxImages)").elsaFont(ElsaFont.caption)
                            }
                            .foregroundStyle(ElsaColor.onSurfaceVariant)
                            .frame(width: ElsaSize.imageThumbnail, height: ElsaSize.imageThumbnail)
                            .background(ElsaColor.fill, in: RoundedRectangle(cornerRadius: ElsaRadius.md, style: .continuous))
                        }
                        .accessibilityLabel(String(localized: "community_add_photo"))
                    }
                }
            }

            if let error = state.error {
                ElsaInlineMessage(message: error.localizedMessage, type: .error)
            }
        } bottomBar: {
            ElsaButton(
                String(localized: state.isEditing ? "community_save" : "community_post"),
                enabled: state.canSave || state.isSaving,
                isLoading: state.isSaving
            ) { viewModel.save() }
        }
        .elsaScreenBackground()
        .navigationTitle(String(localized: state.isEditing ? "community_edit_title" : "community_new_title"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
        .elsaToast($toast)
        .elsaActionSheet(isPresented: $showAdd, title: String(localized: "community_add_photo"), actions: [
            ElsaAction(label: String(localized: "attach_photo"), icon: "elsa_ic_image") { showPhotos = true },
            ElsaAction(label: String(localized: "attach_camera"), icon: "elsa_ic_camera") { openCamera() },
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
                    if let preview = previews[local.key] { Image(uiImage: preview).resizable().scaledToFill() } else { ElsaColor.fill }
                case .remote(let remote):
                    ElsaPostImage(url: remote.url)
                }
            }
            .frame(width: ElsaSize.imageThumbnail, height: ElsaSize.imageThumbnail)
            .clipShape(RoundedRectangle(cornerRadius: ElsaRadius.md, style: .continuous))
            Button(action: onRemove) {
                ElsaIcon("elsa_ic_close", size: 14)
                    .foregroundStyle(ElsaColor.onPrimary)
                    .frame(width: 24, height: 24)
                    .background(ElsaColor.scrim, in: Circle())
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
                toast = ElsaToast(message: AttachmentFailure.from(error).message, type: .error)
            }
        }
    }

    private func loadPhoto(_ item: PhotosPickerItem) async {
        guard let data = try? await item.loadTransferable(type: Data.self), let image = UIImage(data: data) else {
            toast = ElsaToast(message: AttachmentFailure.unreadable.message, type: .error)
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
                toast = ElsaToast(message: String(localized: "error_too_many_images"), type: .error)
            }
        } catch {
            toast = ElsaToast(message: AttachmentFailure.from(error).message, type: .error)
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
