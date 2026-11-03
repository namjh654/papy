package io.github.namjh654.papy.core.data.social

import io.github.namjh654.papy.core.data.config.AppConfig
import io.github.namjh654.papy.core.data.push.devicePlatform
import io.github.namjh654.papy.core.data.session.SessionListener
import io.github.namjh654.papy.core.data.session.UserStore
import kotlinx.serialization.Serializable

/**
 * 소셜 로그인 제공자(DEC-105). 서버에는 [id] 로 보낸다.
 * Swift: `SocialProvider.kakao` 등(SKIE enum), `provider.id`
 */
@Serializable
enum class SocialProvider(val id: String) {
    Kakao("kakao"),
    Google("google"),
    Apple("apple"),
    Naver("naver"),
}

/**
 * 플랫폼 SDK 가 인증한 결과. KMP 가 이것을 서버와 교환해 세션을 시작한다(DEC-109).
 * - [token]: 카카오·네이버 access token, 구글·애플 ID token
 * - [authorizationCode]: 애플(탈퇴 때 서버가 토큰을 폐기하는 데 쓴다), 없으면 null
 * - [nonce]: ID token 을 요청할 때 쓴 값(구글·애플), 없으면 null
 *
 * Swift: `SocialCredential(provider:token:authorizationCode:nonce:)` — 기본값 인자를 쓰지 않으므로 모두 넘긴다
 */
data class SocialCredential(
    val provider: SocialProvider,
    val token: String,
    val authorizationCode: String? = null,
    val nonce: String? = null,
)

/**
 * 소셜 SDK 의 플랫폼 구현 계약(Android Kotlin, iOS Swift). 로그인 자체는 화면(Activity·ViewController)이 SDK 를 부르고
 * 결과를 `LoginViewModel` 에 넘긴다. 여기에는 세션이 끝날 때 SDK 쪽 정리만 둔다.
 * `PlatformServices(socialAuth = …)` 로 넘기며, 넘기지 않으면 [NoOpSocialAuthProvider].
 */
interface SocialAuthProvider {
    /** 로그아웃 직전: 해당 제공자의 SDK 세션을 끝낸다(다음 로그인 때 계정 선택이 다시 뜨게) */
    fun signOut(provider: SocialProvider)

    /** 회원 탈퇴 뒤: 제공자와의 앱 연결을 끊는다(카카오 연결 끊기·네이버 토큰 삭제·구글 연결 해제). 애플은 서버가 토큰을 폐기한다 */
    fun unlink(provider: SocialProvider)
}

/** 소셜 SDK 를 쓰지 않음 — 빌드·실행은 같다 */
object NoOpSocialAuthProvider : SocialAuthProvider {
    override fun signOut(provider: SocialProvider) = Unit
    override fun unlink(provider: SocialProvider) = Unit
}

/**
 * 로그인 화면에 보일 제공자. env 의 키가 있는 제공자만(DEC-105~109). 순서는 표시 순서.
 * - 카카오 `social.kakao.appKey`, 네이버 `social.naver.clientId`
 * - 구글: Android `social.google.webClientId`, iOS `social.google.iosClientId`
 * - 애플: iOS 에서 `social.apple.enabled=true` (Android 는 제외 — DEC-109)
 * - `social.mock=true`(dev 전용, DEC-108): SDK 없이 가짜 인증 값으로 목업 서버와 흐름을 확인한다 → 플랫폼에 맞는 제공자 전부
 */
object SocialConfig {
    val isMock: Boolean get() = AppConfig.SOCIAL_MOCK

    val providers: List<SocialProvider> get() = available(
        platform = devicePlatform,
        mock = AppConfig.SOCIAL_MOCK,
        kakaoAppKey = AppConfig.SOCIAL_KAKAO_APP_KEY,
        googleWebClientId = AppConfig.SOCIAL_GOOGLE_WEB_CLIENT_ID,
        googleIosClientId = AppConfig.SOCIAL_GOOGLE_IOS_CLIENT_ID,
        appleEnabled = AppConfig.SOCIAL_APPLE_ENABLED,
        naverClientId = AppConfig.SOCIAL_NAVER_CLIENT_ID,
    )

    /** 값만으로 정해지는 규칙(테스트용으로 분리) */
    internal fun available(
        platform: String,
        mock: Boolean,
        kakaoAppKey: String,
        googleWebClientId: String,
        googleIosClientId: String,
        appleEnabled: Boolean,
        naverClientId: String,
    ): List<SocialProvider> {
        val ios = platform == "ios"
        return buildList {
            if (mock || kakaoAppKey.isNotBlank()) add(SocialProvider.Kakao)
            if (mock || (if (ios) googleIosClientId else googleWebClientId).isNotBlank()) add(SocialProvider.Google)
            if (ios && (mock || appleEnabled)) add(SocialProvider.Apple)
            if (mock || naverClientId.isNotBlank()) add(SocialProvider.Naver)
        }
    }
}

/** 로그아웃 직전에 소셜로 로그인한 사용자면 그 제공자의 SDK 세션도 끝낸다 */
internal class SocialSessionCleaner(
    private val socialAuth: SocialAuthProvider,
    private val userStore: UserStore,
) : SessionListener {
    override suspend fun onSessionEnding() {
        userStore.user.value?.provider?.let(socialAuth::signOut)
    }
}
