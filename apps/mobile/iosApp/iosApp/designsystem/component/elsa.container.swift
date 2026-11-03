import SwiftUI

// MARK: - Card

/// 테두리·그림자 없는 카드. `.elsaScreenBackground(grouped: true)` 배경 위에 놓는다. (Android `ElsaCard`)
struct ElsaCard<Content: View>: View {
    private let spacing: CGFloat
    private let content: () -> Content

    init(spacing: CGFloat = ElsaSpacing.md, @ViewBuilder content: @escaping () -> Content) {
        self.spacing = spacing
        self.content = content
    }

    var body: some View {
        VStack(alignment: .leading, spacing: spacing) {
            content()
        }
        .padding(ElsaSpacing.xl)
        .frame(maxWidth: .infinity, alignment: .leading)
        .foregroundStyle(ElsaColor.onSurface)
        .background(ElsaColor.surface, in: RoundedRectangle(cornerRadius: ElsaRadius.xl, style: .continuous))
    }
}

// MARK: - Loading

struct ElsaLoadingIndicator: View {
    var body: some View {
        ProgressView()
            .controlSize(.large)
            .tint(ElsaColor.primary)
    }
}

extension View {
    /// 화면 전체를 덮는 로딩. 표시 중에는 아래 화면 터치를 막는다. (Android `ElsaLoadingOverlay`)
    func elsaLoadingOverlay(_ isLoading: Bool, message: String? = nil) -> some View {
        overlay {
            if isLoading {
                ZStack {
                    ElsaColor.scrim.ignoresSafeArea()
                    VStack(spacing: ElsaSpacing.md) {
                        ElsaLoadingIndicator()
                        if let message {
                            Text(message)
                                .elsaFont(ElsaFont.bodySmall)
                                .foregroundStyle(ElsaColor.onSurfaceVariant)
                        }
                    }
                    .padding(ElsaSpacing.xl)
                    .background(ElsaColor.surface, in: RoundedRectangle(cornerRadius: ElsaRadius.lg, style: .continuous))
                }
                .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: ElsaMotion.fast), value: isLoading)
    }
}

// MARK: - Screen

extension View {
    /// 화면 기본 배경 (safe area 까지). 입력·흐름 화면은 background, 목록·대시보드는 grouped(backgroundGrouped).
    func elsaScreenBackground(grouped: Bool = false) -> some View {
        background((grouped ? ElsaColor.backgroundGrouped : ElsaColor.background).ignoresSafeArea())
    }
}

// MARK: - Screen Header

/// 화면 상단의 큰 제목 + 설명. 본문 첫 요소로 쓴다(내비게이션 바 제목은 작게/생략). (Android `ElsaScreenHeader`)
struct ElsaScreenHeader: View {
    let title: String
    var description: String? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: ElsaSpacing.sm) {
            Text(title)
                .elsaFont(ElsaFont.headline)
                .foregroundStyle(ElsaColor.onBackground)
                .fixedSize(horizontal: false, vertical: true)
                // VoiceOver: 제목으로 알린다(제목 사이를 건너뛰며 읽을 수 있다)
                .accessibilityAddTraits(.isHeader)
            if let description {
                Text(description)
                    .elsaFont(ElsaFont.body)
                    .foregroundStyle(ElsaColor.onSurfaceVariant)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.bottom, ElsaSpacing.md)
    }
}
