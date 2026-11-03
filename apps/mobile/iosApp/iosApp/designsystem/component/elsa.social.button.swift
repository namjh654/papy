import Core
import SwiftUI

/// 소셜 로그인 버튼(DEC-119). 배경·글자색과 문구는 각 사 가이드, 크기·모서리는 우리 토큰. Android `ElsaSocialButton` 과 같다.
/// 로고는 템플릿에 넣지 않는다 — 각 사 공식 가이드의 로고를 Assets 에 넣고 `SocialBrandLogo` 에 연결한다(docs/template-setup.md "소셜 로그인").
struct ElsaSocialButton: View {
    let provider: SocialProvider
    var enabled: Bool = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: ElsaSpacing.sm) {
                if let logo = SocialBrandLogo.of(provider) {
                    Image(logo)
                        .resizable()
                        .scaledToFit()
                        .frame(width: ElsaSize.icon, height: ElsaSize.icon)
                        .accessibilityHidden(true)
                }
                Text(String(localized: provider.label))
                    .elsaFont(ElsaFont.button)
                    .lineLimit(1)
            }
            .foregroundStyle(foreground)
            .padding(.horizontal, ElsaSpacing.xl)
            .frame(maxWidth: .infinity, minHeight: ElsaSize.controlHeight)
            .background(background, in: RoundedRectangle(cornerRadius: ElsaRadius.lg, style: .continuous))
            .overlay {
                if provider == .google {
                    RoundedRectangle(cornerRadius: ElsaRadius.lg, style: .continuous)
                        .strokeBorder(ElsaColor.googleOutline, lineWidth: ElsaSize.border)
                }
            }
            // 비활성(다른 로그인 진행 중)이어도 브랜드 색은 유지하고 흐리게만
            .opacity(enabled ? 1 : 0.5)
            .contentShape(Rectangle())
        }
        .buttonStyle(ElsaPressableButtonStyle())
        .disabled(!enabled)
    }

    private var background: Color {
        switch provider {
        case .kakao: ElsaColor.kakao
        case .naver: ElsaColor.naver
        case .google: ElsaColor.google
        case .apple: ElsaColor.apple
        }
    }

    private var foreground: Color {
        switch provider {
        case .kakao: ElsaColor.onKakao
        case .naver: ElsaColor.onNaver
        case .google: ElsaColor.onGoogle
        case .apple: ElsaColor.onApple
        }
    }
}

/// 로그인 화면의 소셜 영역: "또는" 구분선 + 제공자 버튼(표시 순서는 KMP `SocialConfig`)
struct ElsaSocialLoginSection: View {
    let providers: [SocialProvider]
    var enabled: Bool = true
    let onTap: (SocialProvider) -> Void

    var body: some View {
        if !providers.isEmpty {
            VStack(spacing: ElsaSpacing.sm) {
                HStack(spacing: ElsaSpacing.md) {
                    Rectangle().fill(ElsaColor.divider).frame(height: 1)
                    Text("login_social_divider")
                        .elsaFont(ElsaFont.caption)
                        .foregroundStyle(ElsaColor.onSurfaceTertiary)
                    Rectangle().fill(ElsaColor.divider).frame(height: 1)
                }
                ForEach(providers, id: \.self) { provider in
                    ElsaSocialButton(provider: provider, enabled: enabled) { onTap(provider) }
                }
            }
        }
    }
}

/// 각 사 공식 로고(앱마다 추가). 넣기 전에는 nil → 문구만 보인다
enum SocialBrandLogo {
    static func of(_ provider: SocialProvider) -> String? {
        switch provider {
        case .kakao: nil // "social_logo_kakao"
        case .naver: nil // "social_logo_naver"
        case .google: nil // "social_logo_google"
        case .apple: nil // 애플은 SF Symbol "apple.logo" 를 쓸 수 있다(가이드)
        }
    }
}

private extension SocialProvider {
    var label: String.LocalizationValue {
        switch self {
        case .kakao: "social_kakao"
        case .naver: "social_naver"
        case .google: "social_google"
        case .apple: "social_apple"
        }
    }
}


#Preview("소셜 로그인") {
    VStack(spacing: ElsaSpacing.lg) {
        ElsaSocialLoginSection(providers: [.kakao, .google, .apple, .naver], onTap: { _ in })
        ElsaSocialButton(provider: .kakao, enabled: false, action: {})
    }
    .padding(ElsaSpacing.xl)
    .elsaScreenBackground()
}
