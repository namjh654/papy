package cloud.elsa.app.core.common.error

/**
 * 화면에 보여줄 오류의 종류입니다.
 *
 * KMP 는 문구를 갖지 않고 코드만 전달합니다. 실제 문구(다국어)는 플랫폼 리소스가 결정합니다.
 * - Android: `ErrorCode.toStringRes()` → `res/values/strings.xml`, `res/values-en/strings.xml`
 * - iOS: `ErrorCode.localizedMessage` → `Localizable.xcstrings`
 *
 * 코드를 추가하면 양 플랫폼 매핑이 exhaustive `when` / `switch` 라 컴파일 단계에서 누락이 드러납니다.
 */
enum class ErrorCode {
    /** 아이디와 비밀번호를 모두 입력해야 함 */
    CredentialsRequired,

    /** 아이디 또는 비밀번호가 맞지 않음 — 로그인 요청의 401 (세션 만료 [Unauthorized] 와 구분) */
    InvalidCredentials,

    /** 소셜 로그인 실패 — SDK 오류 또는 서버가 소셜 토큰을 거절(DEC-107) */
    SocialLoginFailed,
    UsernameRequired,
    PasswordRequired,
    PasswordMismatch,

    /** 새 비밀번호가 최소 길이보다 짧음 */
    PasswordTooShort,

    /** 인증 코드를 입력해야 함 */
    VerificationCodeRequired,

    NameRequired,

    /** 이메일 형식이 아님 */
    InvalidEmail,

    /** 글·댓글 내용을 입력해야 함(커뮤니티, 20) */
    ContentRequired,

    /** 글·댓글이 최대 길이보다 김 */
    ContentTooLong,

    /** 사진을 너무 많이 넣음 */
    TooManyImages,

    /** 그 밖의 입력값 검증 실패 */
    InvalidInput,
    SignupFailed,

    /** 401 */
    Unauthorized,

    /** 401 을 제외한 4xx */
    RequestRejected,

    /** 5xx 등 */
    ServerError,
    Network,

    /** 응답 JSON 이 계약과 다르거나 필수 값(토큰 등)이 없음 */
    InvalidResponse,
    Unknown,
}

/**
 * UiState 에 담기는 오류 표시 정보입니다.
 * [statusCode] 는 HTTP 오류일 때만 채워지며, 문구에 함께 표시할 수 있습니다.
 */
data class UiError(
    val code: ErrorCode,
    val statusCode: Int? = null,
)
