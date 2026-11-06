import CryptoKit
import Foundation

/// 명조·굵게 대체 글꼴(Noto Serif KR Regular·Bold, Noto Sans KR Bold, OFL-1.1). 앱에 넣지 않고 사용자가 고를 때만 받는다.
/// 엔진은 앱에 넣은 한글 글꼴과 같은 폴더의 글꼴을 모두 읽으므로 같은 폴더(Application Support/EngineFonts)에 둔다.
/// 엔진은 처음 쓸 때 글꼴을 한 번 읽어 두므로, 받은 글꼴은 앱을 다시 열면 쓰인다.
/// 받는 곳: GitHub Releases(fonts-v1), 안 되면 jsDelivr(같은 태그). 받은 파일은 SHA-256 으로 확인한다. (Android `FontPack`)
enum PapyFontPack {
    private struct Font {
        let file: String
        let bytes: Int64
        let sha256: String
    }

    private static let fonts = [
        Font(file: "NotoSerifKR-Regular.ttf", bytes: 8_434_736, sha256: "ad1800d5b4fd538aeab7d2d6e528dea3bd77a79744c9cf32c81072e7ec9a2b86"),
        Font(file: "NotoSerifKR-Bold.ttf", bytes: 8_439_212, sha256: "e7b59b63b716f68996c042a376ac36c6fbefd151cca11b50025805655e9b3af7"),
        Font(file: "NotoSansKR-Bold.ttf", bytes: 2_474_372, sha256: "7db7302f86db389083623d100701c1c9811c2ad11e88c27c1b4f99a0b3e9cb8c"),
    ]
    private static let sources = [
        "https://github.com/namjh654/papy/releases/download/fonts-v1/",
        "https://cdn.jsdelivr.net/gh/namjh654/papy@fonts-v1/",
    ]

    /// 받을 크기(바이트)
    static let totalBytes: Int64 = fonts.reduce(0) { $0 + $1.bytes }

    /// 엔진 글꼴 폴더. 캐시 폴더와 달리 시스템이 지우지 않는다(백업에서는 뺀다).
    static var folder: URL? {
        guard var url = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first?
            .appendingPathComponent("EngineFonts", isDirectory: true)
        else { return nil }
        try? FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try? url.setResourceValues(values)
        return url
    }

    static var installed: Bool {
        guard let folder else { return false }
        return fonts.allSatisfy { size(folder.appendingPathComponent($0.file)) == $0.bytes }
    }

    /// 모두 받는다. `progress` 는 0~1. 실패하면 오류(받다 만 파일은 남기지 않는다).
    static func download(progress: @escaping @Sendable (Double) -> Void) async throws {
        guard let folder else { throw URLError(.cannotCreateFile) }
        var done: Int64 = 0
        for font in fonts {
            let target = folder.appendingPathComponent(font.file)
            if size(target) == font.bytes {
                done += font.bytes
                continue
            }
            var lastError: Error?
            for base in sources {
                guard let url = URL(string: base + font.file) else { continue }
                do {
                    let (temporary, response) = try await URLSession.shared.download(from: url)
                    defer { try? FileManager.default.removeItem(at: temporary) }
                    guard (response as? HTTPURLResponse)?.statusCode == 200 else { throw URLError(.badServerResponse) }
                    let data = try Data(contentsOf: temporary, options: .mappedIfSafe)
                    let digest = SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
                    guard Int64(data.count) == font.bytes, digest == font.sha256 else { throw URLError(.cannotDecodeContentData) }
                    try? FileManager.default.removeItem(at: target)
                    try FileManager.default.moveItem(at: temporary, to: target)
                    lastError = nil
                    break
                } catch {
                    lastError = error
                }
            }
            if let lastError { throw lastError }
            done += font.bytes
            progress(Double(done) / Double(totalBytes))
        }
    }

    private static func size(_ url: URL) -> Int64 {
        ((try? FileManager.default.attributesOfItem(atPath: url.path)[.size]) as? NSNumber)?.int64Value ?? -1
    }
}
