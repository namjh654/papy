import LocalAuthentication

/// 본인 확인(26-1): Face ID·Touch ID, 안 되면 기기 암호(시스템 화면). (Android `DeviceAuth` — BiometricPrompt)
/// `.deviceOwnerAuthentication` 은 생체 인증이 실패하거나 없으면 기기 암호로 넘어간다. Face ID 문구는 Info.plist `NSFaceIDUsageDescription`.
enum DeviceAuth {

    /// 기기에 잠금 수단(생체·암호)이 있는지 — 없으면 설정의 앱 잠금 행을 숨긴다
    static var isAvailable: Bool {
        LAContext().canEvaluatePolicy(.deviceOwnerAuthentication, error: nil)
    }

    /// 성공하면 true. 취소·실패는 false(잠금 화면에 머문다)
    static func authenticate(reason: String) async -> Bool {
        do {
            return try await LAContext().evaluatePolicy(.deviceOwnerAuthentication, localizedReason: reason)
        } catch {
            return false
        }
    }
}
