package cloud.elsa.app.core.data.connectivity

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 네트워크 연결 상태(라이브러리 없이 플랫폼 API 로 직접 구현).
 * - Android: ConnectivityManager 기본 네트워크 콜백 (ACCESS_NETWORK_STATE 권한)
 * - iOS: Network 프레임워크 NWPathMonitor
 *
 * 루트 화면이 [isOnline] 을 관찰해 오프라인 안내(ElsaOfflineBanner)를 보여준다.
 * Swift: `IOSAppGraph.shared.networkMonitor().isOnline` (SkieSwiftStateFlow<KotlinBoolean> → `.boolValue`)
 */
interface NetworkMonitor {
    val isOnline: StateFlow<Boolean>
}

/** 테스트·미리보기용: 값을 직접 바꾼다 */
class FakeNetworkMonitor(online: Boolean = true) : NetworkMonitor {
    override val isOnline = MutableStateFlow(online)
}
