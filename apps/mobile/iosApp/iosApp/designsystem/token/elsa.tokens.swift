// 자동 생성 파일 — 수정 금지. 원본: design/tokens.json · 생성: ./gradlew generateDesignTokens

import CoreText
import SwiftUI
import UIKit

/// 의미 기반(semantic) 색상. light/dark 는 시스템 설정에 따라 자동 전환된다.
enum ElsaColor {
    static let primary = Color(elsaLight: 0x3452DBFF, dark: 0x8FA4FFFF)
    static let onPrimary = Color(elsaLight: 0xFFFFFFFF, dark: 0x0E1B63FF)
    static let primaryContainer = Color(elsaLight: 0xECF0FFFF, dark: 0x1F2E7AFF)
    static let onPrimaryContainer = Color(elsaLight: 0x1A2C8FFF, dark: 0xDCE3FFFF)
    static let secondary = Color(elsaLight: 0x4E5566FF, dark: 0xC3C8D4FF)
    static let onSecondary = Color(elsaLight: 0xFFFFFFFF, dark: 0x22262FFF)
    static let secondaryContainer = Color(elsaLight: 0xF1F2F5FF, dark: 0x2A2D35FF)
    static let onSecondaryContainer = Color(elsaLight: 0x333844FF, dark: 0xE4E6ECFF)
    static let background = Color(elsaLight: 0xFFFFFFFF, dark: 0x121317FF)
    static let onBackground = Color(elsaLight: 0x191B20FF, dark: 0xECEDF1FF)
    static let backgroundGrouped = Color(elsaLight: 0xF3F4F7FF, dark: 0x0B0C0FFF)
    static let surface = Color(elsaLight: 0xFFFFFFFF, dark: 0x1C1D22FF)
    static let onSurface = Color(elsaLight: 0x191B20FF, dark: 0xECEDF1FF)
    static let surfaceVariant = Color(elsaLight: 0xF3F4F7FF, dark: 0x25272DFF)
    static let onSurfaceVariant = Color(elsaLight: 0x6A7180FF, dark: 0xA3A9B6FF)
    static let onSurfaceTertiary = Color(elsaLight: 0x9BA1ACFF, dark: 0x747A87FF)
    static let fill = Color(elsaLight: 0xF2F3F6FF, dark: 0x26282EFF)
    static let fillPressed = Color(elsaLight: 0xE6E8ECFF, dark: 0x30323AFF)
    static let divider = Color(elsaLight: 0xEEEFF2FF, dark: 0x2A2C33FF)
    static let outline = Color(elsaLight: 0xDDE0E6FF, dark: 0x363943FF)
    static let outlineStrong = Color(elsaLight: 0x9197A3FF, dark: 0x6E7482FF)
    static let disabled = Color(elsaLight: 0xEEF0F3FF, dark: 0x2A2C32FF)
    static let onDisabled = Color(elsaLight: 0xA7ACB6FF, dark: 0x5F6470FF)
    static let scrim = Color(elsaLight: 0x00000066, dark: 0x000000B3)
    static let error = Color(elsaLight: 0xD92D20FF, dark: 0xFF8A80FF)
    static let onError = Color(elsaLight: 0xFFFFFFFF, dark: 0x5C0B05FF)
    static let errorContainer = Color(elsaLight: 0xFEE4E2FF, dark: 0x7A1A12FF)
    static let onErrorContainer = Color(elsaLight: 0x7A1A12FF, dark: 0xFEE4E2FF)
    static let success = Color(elsaLight: 0x12A150FF, dark: 0x4ADE80FF)
    static let onSuccess = Color(elsaLight: 0xFFFFFFFF, dark: 0x053B1FFF)
    static let successContainer = Color(elsaLight: 0xDCFCE7FF, dark: 0x0B5130FF)
    static let onSuccessContainer = Color(elsaLight: 0x0B5130FF, dark: 0xDCFCE7FF)
    static let warning = Color(elsaLight: 0xB54708FF, dark: 0xFDB022FF)
    static let onWarning = Color(elsaLight: 0xFFFFFFFF, dark: 0x4E1D09FF)
    static let warningContainer = Color(elsaLight: 0xFEF0C7FF, dark: 0x7A2E0EFF)
    static let onWarningContainer = Color(elsaLight: 0x7A2E0EFF, dark: 0xFEF0C7FF)
    static let info = Color(elsaLight: 0x1570EFFF, dark: 0x84CAFFFF)
    static let onInfo = Color(elsaLight: 0xFFFFFFFF, dark: 0x0B2A5BFF)
    static let infoContainer = Color(elsaLight: 0xD1E9FFFF, dark: 0x194185FF)
    static let onInfoContainer = Color(elsaLight: 0x194185FF, dark: 0xD1E9FFFF)
    static let kakao = Color(elsaLight: 0xFEE500FF, dark: 0xFEE500FF)
    static let onKakao = Color(elsaLight: 0x000000D9, dark: 0x000000D9)
    static let naver = Color(elsaLight: 0x03C75AFF, dark: 0x03C75AFF)
    static let onNaver = Color(elsaLight: 0xFFFFFFFF, dark: 0xFFFFFFFF)
    static let google = Color(elsaLight: 0xFFFFFFFF, dark: 0x131314FF)
    static let onGoogle = Color(elsaLight: 0x1F1F1FFF, dark: 0xE3E3E3FF)
    static let googleOutline = Color(elsaLight: 0x747775FF, dark: 0x8E918FFF)
    static let apple = Color(elsaLight: 0x000000FF, dark: 0xFFFFFFFF)
    static let onApple = Color(elsaLight: 0xFFFFFFFF, dark: 0x000000FF)
}

enum ElsaSpacing {
    static let xxs: CGFloat = 2
    static let xs: CGFloat = 4
    static let sm: CGFloat = 8
    static let md: CGFloat = 12
    static let lg: CGFloat = 16
    static let xl: CGFloat = 24
    static let xxl: CGFloat = 32
    static let xxxl: CGFloat = 48
    static let section: CGFloat = 40
}

enum ElsaRadius {
    static let xs: CGFloat = 6
    static let sm: CGFloat = 10
    static let md: CGFloat = 14
    static let lg: CGFloat = 18
    static let xl: CGFloat = 24
    static let full: CGFloat = 999
}

enum ElsaSize {
    static let controlHeight: CGFloat = 56
    static let controlHeightSmall: CGFloat = 40
    static let checkbox: CGFloat = 22
    static let icon: CGFloat = 20
    static let iconSmall: CGFloat = 16
    static let iconLarge: CGFloat = 24
    static let border: CGFloat = 1
    static let borderFocused: CGFloat = 2
    static let screenPadding: CGFloat = 24
    static let imageThumbnail: CGFloat = 88
    static let listRowHeight: CGFloat = 64
    static let topBarHeight: CGFloat = 56
}

/// 애니메이션/노출 시간(초)
enum ElsaMotion {
    static let fast: Double = 0.15
    static let normal: Double = 0.25
    static let toast: Double = 2.5
}

struct ElsaTextStyle {
    let size: CGFloat
    let lineHeight: CGFloat
    let weight: Font.Weight
    /// 굵기별 글꼴 파일의 PostScript 이름(Pretendard, DEC-117)
    let fontName: String
    /// 시스템 글자 크기(Dynamic Type)를 따를 때 기준 스타일 — 크기에서 정한다(DEC-128)
    let relativeTo: Font.TextStyle

    var font: Font { .custom(fontName, size: size, relativeTo: relativeTo) }
}

enum ElsaFont {
    static let display = ElsaTextStyle(size: 30, lineHeight: 40, weight: .bold, fontName: "Pretendard-Bold", relativeTo: .largeTitle)
    static let headline = ElsaTextStyle(size: 26, lineHeight: 35, weight: .bold, fontName: "Pretendard-Bold", relativeTo: .title)
    static let title = ElsaTextStyle(size: 22, lineHeight: 30, weight: .bold, fontName: "Pretendard-Bold", relativeTo: .title2)
    static let subtitle = ElsaTextStyle(size: 17, lineHeight: 25, weight: .semibold, fontName: "Pretendard-SemiBold", relativeTo: .body)
    static let body = ElsaTextStyle(size: 16, lineHeight: 24, weight: .regular, fontName: "Pretendard-Regular", relativeTo: .body)
    static let bodyStrong = ElsaTextStyle(size: 16, lineHeight: 24, weight: .semibold, fontName: "Pretendard-SemiBold", relativeTo: .body)
    static let bodySmall = ElsaTextStyle(size: 14, lineHeight: 21, weight: .regular, fontName: "Pretendard-Regular", relativeTo: .subheadline)
    static let label = ElsaTextStyle(size: 15, lineHeight: 22, weight: .medium, fontName: "Pretendard-Medium", relativeTo: .subheadline)
    static let caption = ElsaTextStyle(size: 13, lineHeight: 18, weight: .regular, fontName: "Pretendard-Regular", relativeTo: .caption)
    static let button = ElsaTextStyle(size: 17, lineHeight: 24, weight: .semibold, fontName: "Pretendard-SemiBold", relativeTo: .body)

    /// 앱 번들의 글꼴을 등록한다. 앱 시작 때 한 번(Info.plist 수정 없이)
    static func registerFonts() {
        for name in ["Pretendard-Regular", "Pretendard-Medium", "Pretendard-SemiBold", "Pretendard-Bold"] {
            guard let url = Bundle.main.url(forResource: name, withExtension: "otf") else { continue }
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }
}

extension Color {
    /// 0xRRGGBBAA 값 두 개로 light/dark 동적 색을 만든다.
    init(elsaLight light: UInt32, dark: UInt32) {
        self.init(uiColor: UIColor { traits in
            UIColor(elsaRGBA: traits.userInterfaceStyle == .dark ? dark : light)
        })
    }
}

extension UIColor {
    convenience init(elsaRGBA value: UInt32) {
        self.init(
            red: CGFloat((value >> 24) & 0xFF) / 255,
            green: CGFloat((value >> 16) & 0xFF) / 255,
            blue: CGFloat((value >> 8) & 0xFF) / 255,
            alpha: CGFloat(value & 0xFF) / 255
        )
    }
}
