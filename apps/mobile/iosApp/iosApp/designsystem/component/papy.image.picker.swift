import PhotosUI
import SwiftUI
import UIKit

/// 선택된 이미지. 업로드에는 `data` 를, 미리보기에는 `image` 를 사용한다.
struct PapyPickedImage: Identifiable {
    let id = UUID()
    let image: UIImage
    let data: Data
}

/// 이미지 선택 + 썸네일 미리보기. 시스템 PhotosPicker 를 쓰므로 사진 접근 권한이 필요 없다.
/// (Android `PapyImagePicker`)
struct PapyImagePicker: View {
    let label: String?
    @Binding var images: [PapyPickedImage]
    /// 1 이면 단일 선택, 2 이상이면 다중 선택
    var maxCount = 1
    var helperText: String? = nil
    var errorText: String? = nil

    @State private var selection: [PhotosPickerItem] = []
    @State private var isLoading = false
    @Environment(\.isEnabled) private var isEnabled

    var body: some View {
        VStack(alignment: .leading, spacing: PapySpacing.xs) {
            if let label {
                PapyFieldLabel(text: label)
            }

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: PapySpacing.sm) {
                    ForEach(images) { item in
                        thumbnail(item)
                    }
                    if images.count < maxCount {
                        PhotosPicker(
                            selection: $selection,
                            maxSelectionCount: maxCount - images.count,
                            matching: .images
                        ) {
                            addTile
                        }
                        .disabled(isLoading)
                    }
                }
            }

            PapyFieldFooter(helperText: helperText, errorText: errorText)
        }
        .onChange(of: selection) { _, items in
            guard !items.isEmpty else { return }
            Task { await load(items) }
        }
    }

    private func thumbnail(_ item: PapyPickedImage) -> some View {
        Image(uiImage: item.image)
            .resizable()
            .scaledToFill()
            .frame(width: PapySize.imageThumbnail, height: PapySize.imageThumbnail)
            .clipShape(RoundedRectangle(cornerRadius: PapyRadius.md, style: .continuous))
            .overlay(alignment: .topTrailing) {
                if isEnabled {
                    Button {
                        images.removeAll { $0.id == item.id }
                    } label: {
                        PapyIcon("papy_ic_close", size: PapySize.iconSmall)
                            .foregroundStyle(PapyColor.surface)
                            .frame(width: 24, height: 24)
                            .background(PapyColor.scrim, in: Circle())
                    }
                    .padding(PapySpacing.xs)
                    .accessibilityLabel(String(localized: "image_remove"))
                }
            }
    }

    private var addTile: some View {
        VStack(spacing: PapySpacing.xxs) {
            if isLoading {
                ProgressView()
            } else {
                PapyIcon("papy_ic_add", size: PapySize.iconLarge)
                if maxCount > 1 {
                    Text("\(images.count)/\(maxCount)")
                        .papyFont(PapyFont.caption)
                }
            }
        }
        .foregroundStyle(isEnabled ? PapyColor.onSurfaceVariant : PapyColor.onDisabled)
        .frame(width: PapySize.imageThumbnail, height: PapySize.imageThumbnail)
        .background(
            isEnabled ? PapyColor.surfaceVariant : PapyColor.disabled,
            in: RoundedRectangle(cornerRadius: PapyRadius.md, style: .continuous)
        )
        .overlay {
            RoundedRectangle(cornerRadius: PapyRadius.md, style: .continuous)
                .strokeBorder(PapyColor.outline, lineWidth: PapySize.border)
        }
        .accessibilityLabel(String(localized: "image_add"))
    }

    private func load(_ items: [PhotosPickerItem]) async {
        isLoading = true
        var loaded: [PapyPickedImage] = []
        for item in items {
            if let data = try? await item.loadTransferable(type: Data.self),
               let image = UIImage(data: data) {
                loaded.append(PapyPickedImage(image: image, data: data))
            }
        }
        images = Array((images + loaded).prefix(maxCount))
        selection = []
        isLoading = false
    }
}
