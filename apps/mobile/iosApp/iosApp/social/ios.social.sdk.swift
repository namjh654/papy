import AuthenticationServices
import Core
import CryptoKit
import GoogleSignIn
import KakaoSDKAuth
import KakaoSDKCommon
import KakaoSDKUser
import NidCore
import NidThirdPartyLogin
import UIKit

/// 플랫폼 SDK 인증 결과 → 화면이 `LoginViewModel` 의 해당 함수로 넘긴다 (Android `SocialSignInResult`)
enum SocialSignInResult {
    case success(SocialCredential)
    case cancelled
    case failed(Error?)
}

/// 카카오·구글·애플·네이버 SDK(DEC-105~109, 119). 키는 env(`social.*`) → `AppConfig`.
/// 키가 없는 제공자는 초기화하지 않고 KMP `SocialConfig` 가 버튼도 숨긴다.
/// URL 스킴은 `Configuration/Social.local.generated.xcconfig`(커밋 안 함) → Info.plist.
@MainActor
enum IOSSocialSdk {

    /// 네이버 콜백 URL 스킴 — 네이버 개발자센터에 같은 값을 등록한다(docs/template-setup.md)
    static var naverUrlScheme: String { "naverlogin-\(Bundle.main.bundleIdentifier ?? "app")" }

    /// 앱 시작 때 한 번
    static func initialize() {
        let config = AppConfig.shared
        if !config.SOCIAL_KAKAO_APP_KEY.isEmpty {
            KakaoSDK.initSDK(appKey: config.SOCIAL_KAKAO_APP_KEY)
        }
        if !config.SOCIAL_GOOGLE_IOS_CLIENT_ID.isEmpty {
            GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: config.SOCIAL_GOOGLE_IOS_CLIENT_ID)
        }
        if !config.SOCIAL_NAVER_CLIENT_ID.isEmpty {
            NidOAuth.shared.initialize(
                appName: Bundle.main.object(forInfoDictionaryKey: "CFBundleDisplayName") as? String ?? "app",
                clientId: config.SOCIAL_NAVER_CLIENT_ID,
                clientSecret: config.SOCIAL_NAVER_CLIENT_SECRET,
                urlScheme: naverUrlScheme
            )
        }
    }

    /// 소셜 SDK 로 돌아온 URL 이면 처리하고 true(앱 링크 처리보다 먼저).
    /// 키가 있는(초기화한) 제공자만 묻는다 — 카카오는 초기화 전에 부르면 앱이 종료된다(`redirectUri()` 의 `try!`)
    static func handle(url: URL) -> Bool {
        let config = AppConfig.shared
        if !config.SOCIAL_KAKAO_APP_KEY.isEmpty, AuthApi.isKakaoTalkLoginUrl(url) { return AuthController.handleOpenUrl(url: url) }
        if GIDSignIn.sharedInstance.configuration != nil, GIDSignIn.sharedInstance.handle(url) { return true }
        if !config.SOCIAL_NAVER_CLIENT_ID.isEmpty, url.scheme == naverUrlScheme { return NidOAuth.shared.handleURL(url) }
        return false
    }

    static func signIn(_ provider: SocialProvider) async -> SocialSignInResult {
        let result: SocialSignInResult
        switch provider {
        case .kakao: result = await kakao()
        case .google: result = await google()
        case .apple: result = await AppleSignIn().start()
        case .naver: result = await naver()
        }
        if case .failed(let error) = result { print("[PapySocial] \(provider.id) 로그인 실패: \(String(describing: error))") }
        return result
    }

    // MARK: - 카카오

    /// 카카오톡이 있으면 카카오톡으로, 없거나 카카오톡 쪽 오류면 카카오계정(웹)으로. 사용자가 취소하면 넘기지 않는다(카카오 가이드)
    private static func kakao() async -> SocialSignInResult {
        if UserApi.isKakaoTalkLoginAvailable() {
            let talk = await withCheckedContinuation { continuation in
                UserApi.shared.loginWithKakaoTalk { token, error in continuation.resume(returning: kakaoResult(token, error)) }
            }
            if case .failed = talk {} else { return talk }
        }
        return await withCheckedContinuation { continuation in
            UserApi.shared.loginWithKakaoAccount(prompts: nil) { token, error in continuation.resume(returning: kakaoResult(token, error)) }
        }
    }

    private static func kakaoResult(_ token: OAuthToken?, _ error: Error?) -> SocialSignInResult {
        if let token { return .success(SocialCredential(provider: .kakao, token: token.accessToken, authorizationCode: nil, nonce: nil)) }
        if let sdk = error as? SdkError, sdk.isClientFailed, sdk.getClientError().reason == .Cancelled { return .cancelled }
        return .failed(error)
    }

    // MARK: - 구글

    private static func google() async -> SocialSignInResult {
        guard let presenter = topViewController() else { return .failed(nil) }
        let nonce = UUID().uuidString
        return await withCheckedContinuation { continuation in
            GIDSignIn.sharedInstance.signIn(withPresenting: presenter, hint: nil, additionalScopes: nil, nonce: nonce) { result, error in
                if let idToken = result?.user.idToken?.tokenString {
                    continuation.resume(returning: .success(SocialCredential(provider: .google, token: idToken, authorizationCode: nil, nonce: nonce)))
                } else if let error = error as NSError?, error.code == GIDSignInError.canceled.rawValue {
                    continuation.resume(returning: .cancelled)
                } else {
                    continuation.resume(returning: .failed(error))
                }
            }
        }
    }

    // MARK: - 네이버 (NidThirdPartyLogin 5.x)

    private static func naver() async -> SocialSignInResult {
        await withCheckedContinuation { continuation in
            NidOAuth.shared.requestLogin(from: topViewController()) { result in
                switch result {
                case .success(let login):
                    continuation.resume(returning: .success(SocialCredential(provider: .naver, token: login.accessToken.tokenString, authorizationCode: nil, nonce: nil)))
                case .failure(.clientError(.canceledByUser)):
                    continuation.resume(returning: .cancelled)
                case .failure(let error):
                    continuation.resume(returning: .failed(error))
                }
            }
        }
    }

    static func topViewController() -> UIViewController? {
        let window = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first(where: \.isKeyWindow)
        var top = window?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
}

// MARK: - 애플 (AuthenticationServices)

/// 원래 nonce 는 서버로, SHA-256 값은 애플로 보낸다 — 서버가 ID 토큰의 nonce 와 비교한다. 탈퇴 때 토큰 폐기용으로 인가 코드도 보낸다
@MainActor
private final class AppleSignIn: NSObject, ASAuthorizationControllerDelegate, ASAuthorizationControllerPresentationContextProviding {
    private var continuation: CheckedContinuation<SocialSignInResult, Never>?
    private let nonce = UUID().uuidString

    func start() async -> SocialSignInResult {
        await withCheckedContinuation { continuation in
            self.continuation = continuation
            let request = ASAuthorizationAppleIDProvider().createRequest()
            request.requestedScopes = [.fullName, .email]
            request.nonce = SHA256.hash(data: Data(nonce.utf8)).map { String(format: "%02x", $0) }.joined()
            let controller = ASAuthorizationController(authorizationRequests: [request])
            controller.delegate = self
            controller.presentationContextProvider = self
            controller.performRequests()
        }
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
              let idToken = credential.identityToken.flatMap({ String(data: $0, encoding: .utf8) }) else {
            finish(.failed(nil))
            return
        }
        let code = credential.authorizationCode.flatMap { String(data: $0, encoding: .utf8) }
        finish(.success(SocialCredential(provider: .apple, token: idToken, authorizationCode: code, nonce: nonce)))
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        finish((error as? ASAuthorizationError)?.code == .canceled ? .cancelled : .failed(error))
    }

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        IOSSocialSdk.topViewController()?.view.window ?? ASPresentationAnchor()
    }

    private func finish(_ result: SocialSignInResult) {
        continuation?.resume(returning: result)
        continuation = nil
    }
}

/// 로그아웃 직전 SDK 세션 정리(KMP `SocialSessionCleaner` 가 부른다)
final class IOSSocialAuthProvider: SocialAuthProvider {
    func signOut(provider: SocialProvider) {
        Task { @MainActor in
            switch provider {
            case .kakao:
                if !AppConfig.shared.SOCIAL_KAKAO_APP_KEY.isEmpty { UserApi.shared.logout { _ in } }
            case .google:
                GIDSignIn.sharedInstance.signOut()
            case .naver:
                if !AppConfig.shared.SOCIAL_NAVER_CLIENT_ID.isEmpty { NidOAuth.shared.logout() }
            case .apple:
                break // 애플은 앱 쪽 세션이 없다
            }
        }
    }

    /// 회원 탈퇴 뒤 연결 끊기 — 애플은 서버가 인가 코드로 토큰을 폐기한다
    func unlink(provider: SocialProvider) {
        Task { @MainActor in
            switch provider {
            case .kakao:
                if !AppConfig.shared.SOCIAL_KAKAO_APP_KEY.isEmpty { UserApi.shared.unlink { _ in } }
            case .google:
                if GIDSignIn.sharedInstance.configuration != nil { GIDSignIn.sharedInstance.disconnect { _ in } }
            case .naver:
                if !AppConfig.shared.SOCIAL_NAVER_CLIENT_ID.isEmpty { NidOAuth.shared.disconnect { _ in } }
            case .apple:
                break
            }
        }
    }
}
