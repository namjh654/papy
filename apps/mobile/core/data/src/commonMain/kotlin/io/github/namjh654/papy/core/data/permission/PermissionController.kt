package io.github.namjh654.papy.core.data.permission

/** 앱이 실행 중에 요청하는 권한. 쓰지 않는 항목은 여기와 AndroidManifest·Info.plist 에서 같이 지운다 */
enum class AppPermission { Notifications, Camera, Location }

enum class PermissionStatus {
    Granted,

    /** 아직 물어본 적 없다 → 요청하면 시스템 창이 뜬다 */
    NotRequested,

    /** 거부했지만 다시 요청할 수 있다(Android: 한 번 거부) → 왜 필요한지 설명한 뒤 다시 요청 */
    Denied,

    /** 시스템 창이 더는 뜨지 않는다(Android: 두 번 거부·"다시 묻지 않음", iOS: 거부) → 설정 화면으로 보낸다 */
    PermanentlyDenied;

    val isGranted: Boolean get() = this == Granted
}

/**
 * 런타임 권한 공통 창구. 화면(컴포넌트)은 이것만 쓴다.
 *
 * 쓰는 순서(기준: feature:permission):
 * 1. [status] 로 지금 상태를 본다. 화면이 다시 보일 때마다(설정에 다녀온 뒤) 다시 본다 — `lifecycle.doOnResume`
 * 2. Granted 가 아니면 [request]. 결과가 [PermissionStatus.PermanentlyDenied] 면 안내 후 [openSettings]
 */
interface PermissionController {
    suspend fun status(permission: AppPermission): PermissionStatus

    /** 시스템 권한 창을 띄우고 결과를 돌려준다. 이미 허용됐거나 창을 띄울 수 없으면 현재 상태를 돌려준다 */
    suspend fun request(permission: AppPermission): PermissionStatus

    /** 이 앱의 시스템 설정 화면을 연다 */
    fun openSettings()
}

/** 권한 창을 띄울 수 없는 환경(테스트 등): 모두 거부 상태 */
object NoOpPermissionController : PermissionController {
    override suspend fun status(permission: AppPermission) = PermissionStatus.Denied
    override suspend fun request(permission: AppPermission) = PermissionStatus.Denied
    override fun openSettings() = Unit
}
