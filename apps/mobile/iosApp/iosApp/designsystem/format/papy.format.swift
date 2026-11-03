import Foundation
import Core

/// 로케일 기준 날짜·시간·숫자 포맷. 화면에서 dateFormat 패턴("yyyy.MM.dd" 등)을 직접 쓰지 않는다.
/// Android `PapyFormat` 과 같은 역할. SwiftUI 에서는 `@Environment(\.locale)` 을 넘긴다.
enum PapyFormat {

    /// 예) ko: 2026. 9. 29. / en: Sep 29, 2026
    static func date(_ date: Date, locale: Locale = .current) -> String {
        date.formatted(.dateTime.year().month(.abbreviated).day().locale(locale))
    }

    /// 예) ko: 2026년 9월 29일 화요일 / en: Tuesday, September 29, 2026
    static func dateLong(_ date: Date, locale: Locale = .current) -> String {
        date.formatted(.dateTime.year().month(.wide).day().weekday(.wide).locale(locale))
    }

    /// 예) ko: 오후 3:05 / en: 3:05 PM. is24Hour 가 nil 이면 기기 설정을 따른다.
    static func time(_ date: Date, locale: Locale = .current, is24Hour: Bool? = nil) -> String {
        switch is24Hour {
        case .some(true):
            date.formatted(.dateTime.hour(.twoDigits(amPM: .omitted)).minute(.twoDigits).locale(locale))
        case .some(false):
            date.formatted(.dateTime.hour(.defaultDigits(amPM: .abbreviated)).minute(.twoDigits).locale(locale))
        case .none:
            date.formatted(.dateTime.hour().minute().locale(locale))
        }
    }

    /// 시각(epoch 밀리초)의 시:분 — 메시지 시간 등. (Android `PapyFormat.time(millis, …)`)
    static func time(epochMillis: Int64, locale: Locale = .current) -> String {
        time(Date(timeIntervalSince1970: TimeInterval(epochMillis) / 1000), locale: locale)
    }

    /// 파일 크기. 예) 820 B · 12.4 KB · 1.2 MB (1024 단위, Android `PapyFormat.fileSize` 와 같다)
    static func fileSize(_ bytes: Int64, locale: Locale = .current) -> String {
        if bytes < 1024 { return "\(number(Double(bytes), locale: locale)) B" }
        let units = ["KB", "MB", "GB"]
        var value = Double(bytes) / 1024
        var unit = 0
        while value >= 1024 && unit < units.count - 1 {
            value /= 1024
            unit += 1
        }
        return "\(value.formatted(.number.precision(.fractionLength(0...1)).locale(locale))) \(units[unit])"
    }

    /// 예) ko/en: 1,234,567.89 · de: 1.234.567,89
    static func number(_ value: Double, locale: Locale = .current) -> String {
        value.formatted(.number.locale(locale))
    }

    /// 0.256 → 25.6%
    static func percent(_ fraction: Double, locale: Locale = .current) -> String {
        fraction.formatted(.percent.locale(locale))
    }
}

// MARK: - Relative time

extension PapyFormat {
    /// 서버가 준 시각(UTC epoch millis) → 날짜. 예) ko: 2026. 9. 29. / en: Sep 29, 2026
    static func date(epochMillis: Int64, locale: Locale = .current) -> String {
        date(Date(timeIntervalSince1970: TimeInterval(epochMillis) / 1000), locale: locale)
    }

    /// 예) 방금 전 · 5분 전 · 3시간 전 · 2일 전 · 2026. 9. 20. (규칙은 KMP `RelativeTime`, Android `RelativeTime.label()`)
    static func relative(epochMillis: Int64, locale: Locale = .current) -> String {
        switch onEnum(of: RelativeTimeCompanion.shared.since(epochMillis: epochMillis)) {
        case .justNow:
            return String(localized: "time_just_now")
        case .minutes(let value):
            return String(format: String(localized: "time_minutes_ago"), Int64(value.count))
        case .hours(let value):
            return String(format: String(localized: "time_hours_ago"), Int64(value.count))
        case .days(let value):
            return String(format: String(localized: "time_days_ago"), Int64(value.count))
        case .date(let value):
            return date(epochMillis: value.epochMillis, locale: locale)
        }
    }
}
