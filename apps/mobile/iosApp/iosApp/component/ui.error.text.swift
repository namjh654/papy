import Foundation
import Core

/// KMP 의 `ErrorCode` → Localizable.xcstrings 키. 코드가 추가되면 exhaustive switch 라 컴파일 오류로 드러난다.
/// (Android `ErrorCode.toStringRes()` 와 같은 키)
extension ErrorCode {
    var localizationKey: String.LocalizationValue {
        switch self {
        case .credentialsRequired: "error_credentials_required"
        case .invalidCredentials: "error_invalid_credentials"
        case .socialLoginFailed: "error_social_login_failed"
        case .usernameRequired: "error_username_required"
        case .passwordRequired: "error_password_required"
        case .passwordMismatch: "error_password_mismatch"
        case .passwordTooShort: "error_password_too_short"
        case .verificationCodeRequired: "error_code_required"
        case .nameRequired: "error_name_required"
        case .invalidEmail: "error_invalid_email"
        case .contentRequired: "error_content_required"
        case .contentTooLong: "error_content_too_long"
        case .tooManyImages: "error_too_many_images"
        case .invalidInput: "error_invalid_input"
        case .signupFailed: "error_signup_failed"
        case .unauthorized: "error_unauthorized"
        case .requestRejected: "error_request_rejected"
        case .serverError: "error_server"
        case .network: "error_network"
        case .invalidResponse: "error_invalid_response"
        case .unknown: "error_unknown"
        }
    }
}

extension UiError {
    /// 화면에 표시할 오류 문구. HTTP 상태 코드가 있으면 문구 인자로 넘긴다(%1$lld).
    var localizedMessage: String {
        let format = String(localized: code.localizationKey)
        guard let statusCode else { return format }
        return String(format: format, statusCode.int64Value)
    }
}
