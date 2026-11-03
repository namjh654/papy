package cloud.elsa.app.core.data.push

import cloud.elsa.app.core.common.navigation.DeepLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 플랫폼 푸시 코드가 KMP 와 만나는 창구. 모든 함수는 비 suspend 라 Swift·서비스 콜백에서 바로 부른다.
 *
 * - SDK 토큰 콜백 → [onNewToken]
 * - 알림 탭 → [onNotificationOpened] → [pendingDeepLink] 에 보관 → 앱 루트가 관찰해 이동 후 [consumeDeepLink]
 *   (앱이 꺼져 있다가 알림으로 켜져도 UI 가 늦게 구독해 유실되지 않도록 StateFlow)
 * - 권한 요청 결과 → [onPermissionChanged]
 */
class PushManager(
    private val repository: PushRepository,
    val provider: PushProvider,
    private val scope: CoroutineScope,
) {
    private val _pendingDeepLink = MutableStateFlow<DeepLink?>(null)
    val pendingDeepLink: StateFlow<DeepLink?> = _pendingDeepLink.asStateFlow()

    private val _permission = MutableStateFlow(PushPermission.NotDetermined)
    val permission: StateFlow<PushPermission> = _permission.asStateFlow()

    fun onNewToken(token: String) {
        scope.launch { repository.onNewToken(token) }
    }

    /** foreground 에서 받은 메시지를 알림으로 표시할 때 제목·본문·딥링크를 얻는다 */
    fun parse(data: Map<String, String>, title: String?, body: String?): PushMessage =
        PushMessageParser.parse(data, title, body)

    fun onNotificationOpened(data: Map<String, String>) {
        PushMessageParser.parse(data).deepLink?.let { _pendingDeepLink.value = it }
    }

    /**
     * 외부 링크(`elsa://notice/3`, https 링크)나 앱 안의 링크(알림함 항목)로 화면을 연다.
     * 알림 탭과 같은 길을 쓴다: [pendingDeepLink] 에 보관 → 루트가 메인에 들어온 뒤 연다.
     * @return 해석할 수 있는 주소면 true
     */
    fun openLink(uri: String): Boolean {
        val link = DeepLink.parse(uri) ?: return false
        _pendingDeepLink.value = link
        return true
    }

    fun consumeDeepLink() {
        _pendingDeepLink.value = null
    }

    fun onPermissionChanged(permission: PushPermission) {
        _permission.value = permission
    }
}
