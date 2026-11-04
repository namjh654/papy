import Foundation
import PDFKit
import UIKit
import Core

/// iOS 문서 엔진: HWP·HWPX·DOCX 는 Rust 엔진(UniFFI Swift), PDF 는 PDFKit (ADR 0006).
/// KMP `DocumentEngine` 을 구현해 공유 ViewModel 이 쓴다. 쪽 그리기([render])는 화면이 직접 부른다.
/// 문서 내용·경로는 기록하지 않는다.
final class PapyDocumentEngine: NSObject, DocumentEngine, @unchecked Sendable {

    static let shared = PapyDocumentEngine()

    /// 휴대폰 메모리를 생각한 문서 크기 상한
    private static let maxBytes = 256 * 1024 * 1024

    private lazy var rust = PapyEngine(koreanFont: Bundle.main.path(forResource: "NotoSansKR-Regular", ofType: "ttf"))

    private let lock = NSLock()
    private var pdfs: [Int64: PDFDocument] = [:]
    /// PDF 세션 번호는 Rust 세션과 겹치지 않게 큰 수부터
    private var nextPdf: Int64 = 1 << 62

    // MARK: 파일 참조(보안 범위 북마크)

    /// 고른 파일의 참조(북마크 base64). 앱을 다시 켜도 최근 문서로 열 수 있다.
    static func reference(for url: URL) -> String? {
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        return (try? url.bookmarkData())?.base64EncodedString()
    }

    private func readData(_ ref: String) -> (Data?, DocumentError?) {
        guard let bookmark = Data(base64Encoded: ref) else { return (nil, .unreadable) }
        var stale = false
        guard let url = try? URL(resolvingBookmarkData: bookmark, bookmarkDataIsStale: &stale) else {
            return (nil, .unreadable)
        }
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        if let size = try? url.resourceValues(forKeys: [.fileSizeKey]).fileSize, size > Self.maxBytes {
            return (nil, .tooLarge)
        }
        // 파일 앱(iCloud 등)의 파일은 조정된 읽기로 내려받은 뒤 읽는다.
        var data: Data?
        var coordinatorError: NSError?
        NSFileCoordinator().coordinate(readingItemAt: url, options: [], error: &coordinatorError) { readable in
            data = try? Data(contentsOf: readable)
        }
        guard let data else { return (nil, .unreadable) }
        return data.count > Self.maxBytes ? (nil, .tooLarge) : (data, nil)
    }

    // MARK: DocumentEngine

    func open(ref: String, password: String?) -> OpenResult {
        let (read, readError) = readData(ref)
        guard let data = read else { return OpenResult.companion.failure(error: readError ?? .unreadable) }
        if data.starts(with: Array("%PDF-".utf8)) {
            return openPdf(data, password: password)
        }
        do {
            let summary = try rust.open(bytes: data, password: password)
            let kind: DocumentKind = switch summary.format {
            case "hwp": .hwp
            case "hwpx": .hwpx
            default: .docx
            }
            return OpenResult.companion.success(document: OpenedDocument(
                session: Int64(bitPattern: summary.session),
                kind: kind,
                pageCount: Int32(clamping: summary.pageCount),
                title: summary.title,
                droppedObjects: Int32(clamping: summary.droppedObjects)
            ))
        } catch let error as PapyError {
            return OpenResult.companion.failure(error: error.documentError)
        } catch {
            return OpenResult.companion.failure(error: .internal)
        }
    }

    private func openPdf(_ data: Data, password: String?) -> OpenResult {
        guard let document = PDFDocument(data: data) else { return OpenResult.companion.failure(error: .invalid) }
        if document.isLocked {
            guard let password, document.unlock(withPassword: password) else {
                return OpenResult.companion.failure(error: .passwordRequired)
            }
        }
        lock.lock()
        let session = nextPdf
        nextPdf += 1
        pdfs[session] = document
        lock.unlock()
        let title = document.documentAttributes?[PDFDocumentAttribute.titleAttribute] as? String
        return OpenResult.companion.success(document: OpenedDocument(
            session: session, kind: .pdf, pageCount: Int32(document.pageCount), title: title, droppedObjects: 0
        ))
    }

    private func pdf(_ session: Int64) -> PDFDocument? {
        lock.lock()
        defer { lock.unlock() }
        return pdfs[session]
    }

    func pageSize(session: Int64, page: Int32) -> PageSizePt? {
        if let document = pdf(session) {
            guard let pdfPage = document.page(at: Int(page)) else { return nil }
            let bounds = pdfPage.bounds(for: .cropBox)
            // 90°·270° 회전된 쪽은 가로세로를 바꾼다
            let rotated = pdfPage.rotation % 180 != 0
            return PageSizePt(width: Float(rotated ? bounds.height : bounds.width), height: Float(rotated ? bounds.width : bounds.height))
        }
        guard page >= 0, let size = try? rust.pageSize(sessionId: UInt64(bitPattern: session), page: UInt32(page)) else { return nil }
        return PageSizePt(width: size.width, height: size.height)
    }

    func pageText(session: Int64, page: Int32) -> String? {
        if let document = pdf(session) {
            return document.page(at: Int(page))?.string
        }
        guard page >= 0 else { return nil }
        return try? rust.extractText(sessionId: UInt64(bitPattern: session), page: UInt32(page))
    }

    func close(session: Int64) {
        lock.lock()
        let removed = pdfs.removeValue(forKey: session)
        lock.unlock()
        if removed == nil {
            rust.close(sessionId: UInt64(bitPattern: session))
        }
    }

    // MARK: 쪽 그리기

    /// 쪽을 [widthPx] 픽셀 너비로 그린다(흰 배경). 실패하면 nil. 백그라운드에서 부른다.
    func render(session: Int64, page: Int, widthPx: CGFloat) -> UIImage? {
        if let document = pdf(session) {
            guard let pdfPage = document.page(at: page) else { return nil }
            let bounds = pdfPage.bounds(for: .cropBox)
            let rotated = pdfPage.rotation % 180 != 0
            let pageWidth = rotated ? bounds.height : bounds.width
            let pageHeight = rotated ? bounds.width : bounds.height
            let scale = widthPx / max(pageWidth, 1)
            let size = CGSize(width: widthPx, height: pageHeight * scale)
            let format = UIGraphicsImageRendererFormat()
            format.scale = 1
            format.opaque = true
            return UIGraphicsImageRenderer(size: size, format: format).image { context in
                UIColor.white.setFill()
                context.fill(CGRect(origin: .zero, size: size))
                let cg = context.cgContext
                // PDF 좌표(아래가 0)를 화면 좌표로
                cg.translateBy(x: 0, y: size.height)
                cg.scaleBy(x: scale, y: -scale)
                pdfPage.draw(with: .cropBox, to: cg)
            }
        }
        guard page >= 0,
              let size = try? rust.pageSize(sessionId: UInt64(bitPattern: session), page: UInt32(page)),
              let image = try? rust.renderPage(
                sessionId: UInt64(bitPattern: session), page: UInt32(page), scale: Float(widthPx) / max(size.width, 1)
              )
        else { return nil }
        return Self.image(rgba: image.rgba, width: Int(image.width), height: Int(image.height))
    }

    private static func image(rgba: Data, width: Int, height: Int) -> UIImage? {
        guard width > 0, height > 0, rgba.count == width * height * 4,
              let provider = CGDataProvider(data: rgba as CFData),
              let cgImage = CGImage(
                width: width, height: height, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: width * 4,
                space: CGColorSpaceCreateDeviceRGB(),
                bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue),
                provider: provider, decode: nil, shouldInterpolate: true, intent: .defaultIntent
              )
        else { return nil }
        return UIImage(cgImage: cgImage)
    }
}

private extension PapyError {
    var documentError: DocumentError {
        switch self {
        case .PasswordRequired: .passwordRequired
        case .UnsupportedFormat: .unsupported
        case .InvalidDocument: .invalid
        case .LimitExceeded: .tooLarge
        case .Io: .unreadable
        default: .internal
        }
    }
}
