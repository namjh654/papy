package cloud.elsa.app.core.data.security

import cloud.elsa.app.core.data.session.AuthStatus
import cloud.elsa.app.core.data.session.AuthStore
import cloud.elsa.app.core.data.session.SessionListener
import cloud.elsa.app.core.data.storage.LocalStorage
import cloud.elsa.app.core.data.storage.getBoolean
import cloud.elsa.app.core.data.storage.setBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * 앱 잠금(26-1, DEC-134): 켜 두면 앱을 새로 열 때와 [GRACE_MILLIS] 넘게 백그라운드에 있다 돌아올 때 잠근다.
 * 잠금 해제(생체 인증·기기 암호)는 플랫폼이 하고 성공하면 [unlock] — KMP 는 언제 잠글지만 정한다.
 *
 * - 로그인한 동안만 잠근다(로그인 화면은 잠그지 않는다). 다시 로그인하면 풀린 상태로 시작한다(방금 본인 확인을 했다)
 * - 로그아웃(탈퇴 포함)하면 끈다 — 다음 사용자가 앞 사람의 잠금을 받지 않게
 * - 루트 화면이 [isLocked] 를 보고 잠금 화면을 덮는다. 생명 주기: 백그라운드 [onBackground], 돌아옴 [onForeground]
 *
 * Swift: `IOSAppGraph.shared.appLock()` — `isLocked`·`isEnabled` 는 `SkieSwiftStateFlow<KotlinBoolean>`
 */
class AppLock(
    private val storage: LocalStorage,
    authStore: AuthStore,
    private val scope: CoroutineScope,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : SessionListener {

    private val _enabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _enabled.asStateFlow()

    /** 앱을 새로 열면 잠긴 채로 시작한다 */
    private val _locked = MutableStateFlow(true)
    private var backgroundAt: Long? = null

    val isLocked: StateFlow<Boolean> = combine(_enabled, _locked, authStore.status) { enabled, locked, status ->
        enabled && locked && status == AuthStatus.Authenticated
    }.stateIn(scope, SharingStarted.Eagerly, false)

    init {
        scope.launch { _enabled.value = storage.getBoolean(KEY_ENABLED) ?: false }
        // 로그인 화면으로 가면 풀어 둔다 → 다시 로그인하면 잠금 없이 들어간다
        scope.launch { authStore.status.collect { if (it == AuthStatus.Unauthenticated) _locked.value = false } }
    }

    /** 설정의 앱 잠금. 켤 때는 플랫폼이 먼저 본인 확인을 한 뒤 부른다(그래서 풀린 상태로 켠다) */
    suspend fun setEnabled(enabled: Boolean) {
        storage.setBoolean(KEY_ENABLED, enabled)
        _enabled.value = enabled
        _locked.value = false
    }

    /** 플랫폼 본인 확인 성공 */
    fun unlock() {
        _locked.value = false
    }

    /** 앱이 백그라운드로(Android onStop, iOS scenePhase .background) */
    fun onBackground() {
        backgroundAt = now()
    }

    /** 앱이 다시 앞으로. 사진 고르기처럼 잠깐 나갔다 온 것은 잠그지 않는다 */
    fun onForeground() {
        val at = backgroundAt ?: return
        backgroundAt = null
        if (now() - at >= GRACE_MILLIS) _locked.value = true
    }

    override suspend fun onSessionEnding() {
        setEnabled(false)
    }

    companion object {
        /** 이만큼 넘게 나가 있었으면 다시 잠근다 */
        const val GRACE_MILLIS = 30_000L
        internal const val KEY_ENABLED = "security.appLock"
    }
}
