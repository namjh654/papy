import SwiftUI

// MARK: - Card

/// 테두리·그림자 없는 카드. `.papyScreenBackground(grouped: true)` 배경 위에 놓는다. (Android `PapyCard`)
struct PapyCard<Content: View>: View {
    private let spacing: CGFloat
    private let content: () -> Content

    init(spacing: CGFloat = PapySpacing.md, @ViewBuilder content: @escaping () -> Content) {
        self.spacing = spacing
        self.content = content
    }

    var body: some View {
        VStack(alignment: .leading, spacing: spacing) {
            content()
        }
        .padding(PapySpacing.xl)
        .frame(maxWidth: .infinity, alignment: .leading)
        .foregroundStyle(PapyColor.onSurface)
        .background(PapyColor.surface, in: RoundedRectangle(cornerRadius: PapyRadius.xl, style: .continuous))
    }
}

// MARK: - Loading

struct PapyLoadingIndicator: View {
    var body: some View {
        ProgressView()
            .controlSize(.large)
            .tint(PapyColor.primary)
    }
}

extension View {
    /// 화면 전체를 덮는 로딩. 표시 중에는 아래 화면 터치를 막는다. (Android `PapyLoadingOverlay`)
    func papyLoadingOverlay(_ isLoading: Bool, message: String? = nil) -> some View {
        overlay {
            if isLoading {
                ZStack {
                    PapyColor.scrim.ignoresSafeArea()
                    VStack(spacing: PapySpacing.md) {
                        PapyLoadingIndicator()
                        if let message {
                            Text(message)
                                .papyFont(PapyFont.bodySmall)
                                .foregroundStyle(PapyColor.onSurfaceVariant)
                        }
                    }
                    .padding(PapySpacing.xl)
                    .background(PapyColor.surface, in: RoundedRectangle(cornerRadius: PapyRadius.lg, style: .continuous))
                }
                .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: PapyMotion.fast), value: isLoading)
    }
}

// MARK: - Screen

extension View {
    /// 화면 기본 배경 (safe area 까지). 입력·흐름 화면은 background, 목록·대시보드는 grouped(backgroundGrouped).
    func papyScreenBackground(grouped: Bool = false) -> some View {
        background((grouped ? PapyColor.backgroundGrouped : PapyColor.background).ignoresSafeArea())
    }
}

// MARK: - Screen Header

/// 화면 상단의 큰 제목 + 설명. 본문 첫 요소로 쓴다(내비게이션 바 제목은 작게/생략). (Android `PapyScreenHeader`)
struct PapyScreenHeader: View {
    let title: String
    var description: String? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: PapySpacing.sm) {
            Text(title)
                .papyFont(PapyFont.headline)
                .foregroundStyle(PapyColor.onBackground)
                .fixedSize(horizontal: false, vertical: true)
                // VoiceOver: 제목으로 알린다(제목 사이를 건너뛰며 읽을 수 있다)
                .accessibilityAddTraits(.isHeader)
            if let description {
                Text(description)
                    .papyFont(PapyFont.body)
                    .foregroundStyle(PapyColor.onSurfaceVariant)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.bottom, PapySpacing.md)
    }
}
