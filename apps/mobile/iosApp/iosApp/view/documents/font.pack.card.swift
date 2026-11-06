import SwiftUI

/// 명조·굵게 글꼴 받기. 사용자가 누를 때만 받는다(앱은 그 밖에 문서를 네트워크로 보내지 않는다). (Android `FontPackCard`)
struct FontPackCard: View {
    private enum Phase { case idle, downloading, done, failed }

    @State private var phase: Phase = PapyFontPack.installed ? .done : .idle
    @State private var progress = 0.0

    var body: some View {
        PapyCard(spacing: PapySpacing.xs) {
            Text(String(localized: "fonts_title"))
                .papyFont(PapyFont.subtitle)
            Text(message)
                .papyFont(PapyFont.caption)
                .foregroundStyle(PapyColor.onSurfaceVariant)
            if phase == .idle || phase == .failed {
                PapyButton(String(localized: "fonts_download"), style: .secondary, size: .small, fullWidth: false) {
                    phase = .downloading
                    Task {
                        do {
                            try await PapyFontPack.download { value in
                                Task { @MainActor in progress = value }
                            }
                            phase = .done
                        } catch {
                            phase = .failed
                        }
                    }
                }
                .padding(.top, PapySpacing.xs)
                .accessibilityIdentifier("documents_fonts")
            }
        }
    }

    private var message: String {
        switch phase {
        case .idle: return String(format: String(localized: "fonts_description"), Int(PapyFontPack.totalBytes / 1_000_000))
        case .downloading: return String(format: String(localized: "fonts_downloading"), Int(progress * 100))
        case .done: return String(localized: "fonts_done")
        case .failed: return String(localized: "fonts_failed")
        }
    }
}
