package cloud.elsa.app.core.data.push

/**
 * 푸시 제공자(FCM, APNs 직접 등)의 플랫폼 구현 계약. Android 는 Kotlin, iOS 는 Swift 에서 구현해
 * `initAndroidKoin(pushProvider = …)` / `IOSAppKoin.initialize(appEnv:pushProvider:)` 로 넘긴다.
 * 토큰은 제공자 SDK 콜백에서 [PushManager.onNewToken] 으로 KMP 에 알린다.
 */
interface PushProvider {
    /** 서버 등록 시 함께 보내는 제공자 이름: "fcm", "apns", "none" */
    val id: String

    /** 설정 파일이 없는 등으로 푸시를 쓸 수 없으면 false */
    val isEnabled: Boolean

    /** 로그아웃 시 호출된다. 이 기기 토큰을 무효화해 다음 로그인 때 새 토큰을 받는다 */
    fun deleteToken()
}

/** 푸시 비활성(설정 파일 없음) — 빌드·실행은 정상, 토큰·알림만 없다 */
object NoOpPushProvider : PushProvider {
    override val id: String = "none"
    override val isEnabled: Boolean = false
    override fun deleteToken() = Unit
}

/** 알림 권한 상태. 플랫폼이 요청 결과를 [PushManager.onPermissionChanged] 로 알린다 */
enum class PushPermission {
    NotDetermined,
    Granted,
    Denied,
}
