@file:OptIn(ExperimentalForeignApi::class)

package cloud.elsa.app.core.data.permission

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.registerForRemoteNotifications
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

/**
 * iOS 권한. iOS 는 거부하면 시스템 창이 다시 뜨지 않으므로 거부 = [PermissionStatus.PermanentlyDenied](설정으로 보낸다).
 * 요청 전에 Info.plist 의 사용 목적 문구가 있어야 한다: NSCameraUsageDescription, NSLocationWhenInUseUsageDescription
 * (없으면 요청하는 순간 앱이 종료된다).
 */
class IOSPermissionController : PermissionController {

    private val locationManager = CLLocationManager()

    /** 위치 권한 창의 결과를 기다리는 동안 붙잡는다(CLLocationManager 는 delegate 를 약하게 잡는다) */
    private var locationDelegate: LocationDelegate? = null

    override suspend fun status(permission: AppPermission): PermissionStatus = when (permission) {
        AppPermission.Notifications -> notificationStatus()
        AppPermission.Camera -> when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
            AVAuthorizationStatusAuthorized -> PermissionStatus.Granted
            AVAuthorizationStatusNotDetermined -> PermissionStatus.NotRequested
            else -> PermissionStatus.PermanentlyDenied
        }
        AppPermission.Location -> locationManager.authorizationStatus.toStatus()
    }

    override suspend fun request(permission: AppPermission): PermissionStatus {
        // 물어본 적이 없을 때만 시스템 창이 뜬다
        if (status(permission) != PermissionStatus.NotRequested) return status(permission)
        when (permission) {
            AppPermission.Notifications -> requestNotifications()
            AppPermission.Camera -> suspendCancellableCoroutine { continuation ->
                AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { _ -> continuation.resume(Unit) }
            }
            AppPermission.Location -> requestLocation()
        }
        return status(permission)
    }

    override fun openSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        dispatch_async(dispatch_get_main_queue()) {
            UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
        }
    }

    private suspend fun notificationStatus(): PermissionStatus = suspendCancellableCoroutine { continuation ->
        UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler { settings ->
            val status = when (settings?.authorizationStatus) {
                UNAuthorizationStatusAuthorized, UNAuthorizationStatusProvisional, UNAuthorizationStatusEphemeral -> PermissionStatus.Granted
                UNAuthorizationStatusNotDetermined, null -> PermissionStatus.NotRequested
                else -> PermissionStatus.PermanentlyDenied
            }
            continuation.resume(status)
        }
    }

    private suspend fun requestNotifications(): Unit = suspendCancellableCoroutine { continuation ->
        val options = UNAuthorizationOptionAlert or UNAuthorizationOptionBadge or UNAuthorizationOptionSound
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(options) { granted, _ ->
            // 허용되면 원격 알림 등록(→ 푸시 토큰). 메인 스레드에서 불러야 한다
            if (granted) dispatch_async(dispatch_get_main_queue()) { UIApplication.sharedApplication.registerForRemoteNotifications() }
            continuation.resume(Unit)
        }
    }

    private suspend fun requestLocation(): Unit = suspendCancellableCoroutine { continuation ->
        val delegate = LocationDelegate { status ->
            // 창이 뜨기 전에도 한 번 불린다(NotDetermined) — 결정된 뒤의 호출만 받는다
            if (status != kCLAuthorizationStatusNotDetermined && continuation.isActive) {
                locationManager.delegate = null
                locationDelegate = null
                continuation.resume(Unit)
            }
        }
        locationDelegate = delegate
        locationManager.delegate = delegate
        locationManager.requestWhenInUseAuthorization()
        continuation.invokeOnCancellation {
            locationManager.delegate = null
            locationDelegate = null
        }
    }

    private fun CLAuthorizationStatus.toStatus(): PermissionStatus = when (this) {
        kCLAuthorizationStatusAuthorizedAlways, kCLAuthorizationStatusAuthorizedWhenInUse -> PermissionStatus.Granted
        kCLAuthorizationStatusNotDetermined -> PermissionStatus.NotRequested
        else -> PermissionStatus.PermanentlyDenied
    }
}

private class LocationDelegate(
    private val onChanged: (CLAuthorizationStatus) -> Unit,
) : NSObject(), CLLocationManagerDelegateProtocol {
    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
        onChanged(manager.authorizationStatus)
    }
}
