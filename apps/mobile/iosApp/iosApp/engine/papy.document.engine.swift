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

    private lazy var rust = PapyEngine(koreanFont: Self.koreanFont())

    /// 앱에 넣은 한글 글꼴을 글꼴만 든 폴더로 복사한다. PDF 내보내기가 이 폴더의 글꼴을 읽는다
    /// (앱 묶음 폴더를 통째로 뒤지지 않게).
    private static func koreanFont() -> String? {
        guard let bundled = Bundle.main.url(forResource: "NotoSansKR-Regular", withExtension: "ttf"),
              let caches = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first
        else { return nil }
        let folder = caches.appendingPathComponent("EngineFonts", isDirectory: true)
        let target = folder.appendingPathComponent("NotoSansKR-Regular.ttf")
        if !FileManager.default.fileExists(atPath: target.path) {
            try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
            try? FileManager.default.copyItem(at: bundled, to: target)
        }
        return FileManager.default.fileExists(atPath: target.path) ? target.path : bundled.path
    }

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
                droppedObjects: Int32(clamping: summary.droppedObjects),
                canEdit: summary.canEdit
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
            session: session, kind: .pdf, pageCount: Int32(document.pageCount), title: title, droppedObjects: 0, canEdit: false
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

    func pageCount(session: Int64) -> KotlinInt? {
        if let document = pdf(session) { return KotlinInt(int: Int32(document.pageCount)) }
        guard let count = try? rust.pageCount(sessionId: UInt64(bitPattern: session)) else { return nil }
        return KotlinInt(int: Int32(clamping: count))
    }

    func outline(session: Int64) -> [Core.OutlineEntry] {
        if let document = pdf(session) {
            var out: [Core.OutlineEntry] = []
            func walk(_ node: PDFOutline, depth: Int32) {
                for i in 0..<node.numberOfChildren {
                    guard let child = node.child(at: i) else { continue }
                    let page = child.destination?.page.map { KotlinInt(int: Int32(document.index(for: $0))) }
                    out.append(Core.OutlineEntry(title: child.label ?? "", page: page, depth: depth))
                    walk(child, depth: depth + 1)
                }
            }
            if let root = document.outlineRoot { walk(root, depth: 0) }
            return out
        }
        guard let entries = try? rust.outline(sessionId: UInt64(bitPattern: session)) else { return [] }
        return entries.map { entry in
            Core.OutlineEntry(title: entry.title, page: entry.page.map { KotlinInt(int: Int32(clamping: $0)) }, depth: Int32(clamping: entry.depth))
        }
    }

    func findText(session: Int64, query: String, max: Int32) -> [Core.TextHit] {
        if let document = pdf(session) {
            let needle = query.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !needle.isEmpty else { return [] }
            var hits: [Core.TextHit] = []
            for selection in document.findString(needle, withOptions: [.caseInsensitive]).prefix(Int(max)) {
                guard let page = selection.pages.first else { continue }
                let matched = selection.string ?? needle
                // 앞뒤 글자: 고른 범위를 넓혀 읽고 찾은 글자를 기준으로 나눈다.
                let context = selection.copy() as? PDFSelection
                context?.extend(atStart: 24)
                context?.extend(atEnd: 24)
                let around = (context?.string ?? matched).replacingOccurrences(of: "\n", with: " ")
                let range = around.range(of: matched, options: .caseInsensitive)
                hits.append(Core.TextHit(
                    page: Int32(document.index(for: page)),
                    before: range.map { String(around[..<$0.lowerBound]).trimmingCharacters(in: .whitespaces) } ?? "",
                    matched: matched,
                    after: range.map { String(around[$0.upperBound...]).trimmingCharacters(in: .whitespaces) } ?? ""
                ))
            }
            return hits
        }
        guard let hits = try? rust.findText(sessionId: UInt64(bitPattern: session), query: query, max: UInt32(clamping: max)) else { return [] }
        return hits.map { Core.TextHit(page: Int32(clamping: $0.page), before: $0.before, matched: $0.matched, after: $0.after) }
    }

    func unsupportedObjects(session: Int64, page: Int32) -> Core.UnsupportedCount {
        guard pdf(session) == nil, page >= 0,
              let count = try? rust.unsupportedObjects(sessionId: UInt64(bitPattern: session), page: UInt32(page))
        else { return Core.UnsupportedCount(placeholders: 0, missingImages: 0) }
        return Core.UnsupportedCount(placeholders: Int32(clamping: count.placeholders), missingImages: Int32(clamping: count.missingImages))
    }

    func paragraphAt(session: Int64, page: Int32, x: Float, y: Float) -> ParagraphHit? {
        guard pdf(session) == nil, page >= 0,
              let hit = try? rust.paragraphAt(sessionId: UInt64(bitPattern: session), page: UInt32(page), x: x, y: y)
        else { return nil }
        return ParagraphHit(
            section: Int32(clamping: hit.section),
            paragraph: Int32(clamping: hit.paragraph),
            text: hit.text,
            boxes: hit.boxes.map { PageBox(page: Int32(clamping: $0.page), x: $0.x, y: $0.y, width: $0.width, height: $0.height) }
        )
    }

    func replaceParagraph(session: Int64, section: Int32, paragraph: Int32, text: String) -> Int32 {
        guard pdf(session) == nil, section >= 0, paragraph >= 0,
              let depth = try? rust.replaceParagraph(
                sessionId: UInt64(bitPattern: session), section: UInt32(section), paragraph: UInt32(paragraph), text: text
              )
        else { return -1 }
        return Int32(clamping: depth)
    }

    /// Rust 문서만 편집한다. PDF 세션이거나 실패하면 nil
    private func editing<T>(_ session: Int64, _ body: (UInt64) throws -> T) -> T? {
        guard pdf(session) == nil else { return nil }
        return try? body(UInt64(bitPattern: session))
    }

    func caretAt(session: Int64, page: Int32, x: Float, y: Float) -> Caret? {
        guard page >= 0 else { return nil }
        return editing(session) { try rust.caretAtPoint(sessionId: $0, page: UInt32(page), x: x, y: y).caret }
    }

    func caretAtPosition(session: Int64, position: TextPos) -> Caret? {
        editing(session) { try rust.caretAt(sessionId: $0, position: position.engine).caret }
    }

    func moveCaret(session: Int64, position: TextPos, move: CaretMove, preferredX: Float) -> Caret? {
        editing(session) {
            try rust.moveCaret(sessionId: $0, position: position.engine, direction: move.engine, preferredX: preferredX).caret
        }
    }

    func insertText(session: Int64, position: TextPos, text: String) -> EditResult? {
        editing(session) { try rust.insertText(sessionId: $0, position: position.engine, text: text).result }
    }

    func deleteBackward(session: Int64, position: TextPos) -> EditResult? {
        editing(session) { try rust.deleteBackward(sessionId: $0, position: position.engine).result }
    }

    func deleteRange(session: Int64, range: TextRange) -> EditResult? {
        editing(session) { try rust.deleteRange(sessionId: $0, range: range.engine).result }
    }

    func selectionBoxes(session: Int64, range: TextRange) -> [PageBox] {
        editing(session) { try rust.selectionRegions(sessionId: $0, range: range.engine).map(\.box) } ?? []
    }

    func wordAt(session: Int64, position: TextPos) -> TextRange? {
        editing(session) {
            let range = try rust.wordAt(sessionId: $0, position: position.engine)
            return TextRange(start: range.start.pos, end: range.end.pos)
        }
    }

    func textStyle(session: Int64, position: TextPos) -> TextStyle? {
        editing(session) {
            let style = try rust.textStyle(sessionId: $0, position: position.engine)
            return TextStyle(
                fontSize: style.fontSize.map { KotlinFloat(float: $0) },
                bold: style.bold.map { KotlinBoolean(bool: $0) },
                italic: style.italic.map { KotlinBoolean(bool: $0) },
                underline: style.underline.map { KotlinBoolean(bool: $0) },
                strikethrough: style.strikethrough.map { KotlinBoolean(bool: $0) },
                color: style.color
            )
        }
    }

    func applyTextStyle(session: Int64, range: TextRange, style: TextStyle) -> Int32 {
        let engineStyle = EngineTextStyle(
            fontSize: style.fontSize?.floatValue,
            bold: style.bold?.boolValue,
            italic: style.italic?.boolValue,
            underline: style.underline?.boolValue,
            strikethrough: style.strikethrough?.boolValue,
            color: style.color
        )
        return editing(session) {
            Int32(clamping: try rust.applyTextStyle(sessionId: $0, range: range.engine, style: engineStyle))
        } ?? -1
    }

    // MARK: 표·그림

    func pageObjects(session: Int64, page: Int32) -> [PageObject] {
        guard page >= 0 else { return [] }
        return editing(session) {
            try rust.pageObjects(sessionId: $0, page: UInt32(page)).map { object in
                PageObject(
                    kind: object.kind == .table ? .table : .picture,
                    target: object.object.ref,
                    box: object.rect.box,
                    rows: Int32(clamping: object.rows),
                    cols: Int32(clamping: object.cols),
                    cells: object.cells.map { cell in
                        TableCell(
                            cell: Int32(clamping: cell.cell), row: Int32(clamping: cell.row), col: Int32(clamping: cell.col),
                            rowSpan: Int32(clamping: cell.rowSpan), colSpan: Int32(clamping: cell.colSpan), box: cell.rect.box
                        )
                    }
                )
            }
        } ?? []
    }

    func insertTable(session: Int64, position: TextPos, rows: Int32, cols: Int32) -> Int32 {
        guard rows > 0, cols > 0 else { return -1 }
        return editing(session) {
            Int32(clamping: try rust.insertTable(sessionId: $0, position: position.engine, rows: UInt32(rows), cols: UInt32(cols)))
        } ?? -1
    }

    func editTable(
        session: Int64, table: ObjectRef, action: TableAction, row: Int32, col: Int32, endRow: Int32, endCol: Int32
    ) -> Int32 {
        let r = UInt32(clamping: row)
        let c = UInt32(clamping: col)
        let op: EngineTableOp
        switch action {
        case .insertRowAbove: op = .insertRow(row: r, below: false)
        case .insertRowBelow: op = .insertRow(row: r, below: true)
        case .insertColumnLeft: op = .insertColumn(col: c, right: false)
        case .insertColumnRight: op = .insertColumn(col: c, right: true)
        case .deleteRow: op = .deleteRow(row: r)
        case .deleteColumn: op = .deleteColumn(col: c)
        default:
            op = .merge(startRow: r, startCol: c, endRow: UInt32(clamping: endRow), endCol: UInt32(clamping: endCol))
        }
        return editing(session) { Int32(clamping: try rust.editTable(sessionId: $0, table: table.engine, op: op)) } ?? -1
    }

    /// [source]: 고른 그림 파일 경로. 엔진이 못 넣는 형식(HEIC 등)은 JPEG 로 바꾼다.
    func insertPicture(session: Int64, position: TextPos, source: String, width: Float, height: Float) -> Int32 {
        guard let bytes = Self.pictureBytes(path: source) else { return -1 }
        return editing(session) {
            Int32(clamping: try rust.insertPicture(sessionId: $0, position: position.engine, image: bytes, width: width, height: height))
        } ?? -1
    }

    func resizePicture(session: Int64, picture: ObjectRef, width: Float, height: Float) -> Int32 {
        editing(session) {
            Int32(clamping: try rust.resizePicture(sessionId: $0, picture: picture.engine, width: width, height: height))
        } ?? -1
    }

    func deleteObject(session: Int64, target: ObjectRef, kind: ObjectKind) -> Int32 {
        editing(session) {
            Int32(clamping: try rust.deleteObject(sessionId: $0, object: target.engine, kind: kind == .table ? .table : .picture))
        } ?? -1
    }

    /// 그림 바이트(32MB 까지). PNG·JPEG·GIF·BMP 가 아니면 JPEG 로 바꾼다.
    private static func pictureBytes(path: String) -> Data? {
        let url = URL(fileURLWithPath: path)
        guard let size = (try? url.resourceValues(forKeys: [.fileSizeKey]))?.fileSize, size <= maxPictureBytes,
              let data = try? Data(contentsOf: url)
        else { return nil }
        if isEnginePicture(data) { return data }
        guard let jpeg = UIImage(data: data)?.jpegData(compressionQuality: 0.9), jpeg.count <= maxPictureBytes else { return nil }
        return jpeg
    }

    private static let maxPictureBytes = 32 * 1024 * 1024

    private static func isEnginePicture(_ data: Data) -> Bool {
        let head = [UInt8](data.prefix(8))
        return head.starts(with: [0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A])
            || head.starts(with: [0xFF, 0xD8, 0xFF])
            || head.starts(with: Array("GIF8".utf8))
            || head.starts(with: Array("BM".utf8))
    }

    func undo(session: Int64) -> UndoResult? {
        editing(session) {
            let undo = try rust.undo(sessionId: $0)
            return UndoResult(caret: undo.caret?.caret, undoDepth: Int32(clamping: undo.undoDepth))
        }
    }

    /// PDF 로 바꿔 [target](파일 경로)에 쓴다. 화면이 이어서 공유·저장 창을 연다.
    func exportPdf(session: Int64, target: String) -> DocumentError? {
        guard pdf(session) == nil else { return .unsupported }
        let data: Data
        do {
            data = try rust.export(sessionId: UInt64(bitPattern: session), extension: "pdf")
        } catch let error as PapyError {
            return error.documentError
        } catch {
            return .internal
        }
        return (try? data.write(to: URL(fileURLWithPath: target), options: .atomic)) == nil ? .readOnly : nil
    }

    func save(session: Int64, ref: String) -> DocumentError? {
        guard pdf(session) == nil else { return .unsupported }
        let data: Data
        do {
            data = try rust.save(sessionId: UInt64(bitPattern: session))
        } catch let error as PapyError {
            return error.documentError
        } catch {
            return .internal
        }
        guard let bookmark = Data(base64Encoded: ref) else { return .unreadable }
        var stale = false
        guard let url = try? URL(resolvingBookmarkData: bookmark, bookmarkDataIsStale: &stale) else { return .unreadable }
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        // 파일 앱(iCloud 등)이 알도록 조정된 쓰기로, 한 번에 바꾼다(atomic)
        var written = false
        var coordinatorError: NSError?
        NSFileCoordinator().coordinate(writingItemAt: url, options: .forReplacing, error: &coordinatorError) { target in
            written = (try? data.write(to: target, options: .atomic)) != nil
        }
        return written ? nil : .readOnly
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

private extension TextPos {
    var engine: EnginePosition {
        EnginePosition(
            section: UInt32(clamping: section), paragraph: UInt32(clamping: paragraph), offset: UInt32(clamping: offset),
            cell: cell.map {
                EngineCell(control: UInt32(clamping: $0.control), cell: UInt32(clamping: $0.cell), cellParagraph: UInt32(clamping: $0.cellParagraph))
            }
        )
    }
}

private extension EnginePosition {
    var pos: TextPos {
        TextPos(
            section: Int32(clamping: section), paragraph: Int32(clamping: paragraph), offset: Int32(clamping: offset),
            cell: cell.map {
                CellRef(control: Int32(clamping: $0.control), cell: Int32(clamping: $0.cell), cellParagraph: Int32(clamping: $0.cellParagraph))
            }
        )
    }
}

private extension ObjectRef {
    var engine: EngineObjectRef {
        EngineObjectRef(section: UInt32(clamping: section), paragraph: UInt32(clamping: paragraph), control: UInt32(clamping: control))
    }
}

private extension EngineObjectRef {
    var ref: ObjectRef { ObjectRef(section: Int32(clamping: section), paragraph: Int32(clamping: paragraph), control: Int32(clamping: control)) }
}

private extension TextRange {
    var engine: EngineRange { EngineRange(start: start.engine, end: end.engine) }
}

private extension PageRegion {
    var box: PageBox { PageBox(page: Int32(clamping: page), x: x, y: y, width: width, height: height) }
}

private extension EngineCaret {
    var caret: Caret { Caret(position: position.pos, box: rect.box) }
}

private extension EngineEdit {
    var result: EditResult { EditResult(caret: caret.caret, undoDepth: Int32(clamping: undoDepth)) }
}

private extension CaretMove {
    var engine: CaretDirection {
        switch self {
        case .left: .left
        case .right: .right
        case .up: .up
        case .down: .down
        case .lineStart: .lineStart
        default: .lineEnd
        }
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
