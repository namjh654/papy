import AVFoundation
import SwiftUI
import UIKit
import UniformTypeIdentifiers
import Core

/// 첨부를 못 가져온 이유 — 화면이 문구로 바꾼다. (Android `AttachmentFailure`)
enum AttachmentFailure: Error {
    case permissionDenied, unavailable, tooLarge, unreadable

    static func from(_ error: Error) -> AttachmentFailure { error as? AttachmentFailure ?? .unreadable }

    var message: String {
        switch self {
        case .permissionDenied: String(localized: "chat_attach_denied")
        case .unavailable: String(localized: "chat_camera_unavailable")
        case .tooLarge: String(localized: "chat_attach_too_large")
        case .unreadable: String(localized: "chat_attach_unreadable")
        }
    }
}

/// 사진·카메라·파일(DEC-125) 내용을 KMP `AttachmentInput` 으로 만든다. 메시지·커뮤니티가 같이 쓴다. 사진은 긴 변 2048px JPEG 로 줄여 보낸다(Android 와 같다).
enum Attachments {
    /// 한 번에 보낼 수 있는 크기(메모리에 올려 업로드하므로 제한한다). 서버 제한과 맞춘다
    static let maxBytes = 20 * 1024 * 1024
    private static let maxImageSide: CGFloat = 2048

    static func image(_ image: UIImage, name: String = "photo.jpg") throws(AttachmentFailure) -> (input: AttachmentInput, preview: UIImage) {
        let scale = min(1, maxImageSide / max(image.size.width, image.size.height))
        let target = CGSize(width: image.size.width * scale, height: image.size.height * scale)
        let resized = image.preparingThumbnail(of: target) ?? image
        guard let jpeg = resized.jpegData(compressionQuality: 0.85) else { throw .unreadable }
        guard jpeg.count <= maxBytes else { throw .tooLarge }
        let jpegName = (name as NSString).deletingPathExtension + ".jpg"
        let input = AttachmentInput(name: jpegName, mimeType: "image/jpeg", kind: .image, bytes: IOSAppGraph.shared.bytes(data: jpeg))
        return (input, resized)
    }

    /// 파일 앱에서 고른 파일(보안 범위 URL). 사진 파일이면 사진으로 보낸다
    static func file(_ url: URL) throws(AttachmentFailure) -> (input: AttachmentInput, preview: UIImage?) {
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        let size = (try? url.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0
        guard size <= maxBytes else { throw .tooLarge }
        guard let data = try? Data(contentsOf: url) else { throw .unreadable }
        guard data.count <= maxBytes else { throw .tooLarge }
        let type = UTType(filenameExtension: url.pathExtension) ?? .data
        if type.conforms(to: .image), let image = UIImage(data: data) {
            let picked = try self.image(image, name: url.lastPathComponent)
            return (picked.input, picked.preview)
        }
        let mime = type.preferredMIMEType ?? "application/octet-stream"
        let input = AttachmentInput(name: url.lastPathComponent, mimeType: mime, kind: .file, bytes: IOSAppGraph.shared.bytes(data: data))
        return (input, nil)
    }

    /// 카메라를 열 수 있는지: 기기에 카메라가 있고 권한이 거부되지 않았는지. 아직 묻지 않았으면 여기서 묻는다
    static func cameraAccess() async throws(AttachmentFailure) {
        guard UIImagePickerController.isSourceTypeAvailable(.camera) else { throw .unavailable }
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized: return
        case .notDetermined:
            if await AVCaptureDevice.requestAccess(for: .video) { return }
            throw .permissionDenied
        default: throw .permissionDenied
        }
    }
}

/// 시스템 카메라(`UIImagePickerController`)로 한 장 찍는다
struct CameraPicker: UIViewControllerRepresentable {
    let onCaptured: (UIImage) -> Void
    @Environment(\.dismiss) private var dismiss

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = .camera
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        private let parent: CameraPicker

        init(_ parent: CameraPicker) { self.parent = parent }

        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            if let image = info[.originalImage] as? UIImage { parent.onCaptured(image) }
            parent.dismiss()
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            parent.dismiss()
        }
    }
}
