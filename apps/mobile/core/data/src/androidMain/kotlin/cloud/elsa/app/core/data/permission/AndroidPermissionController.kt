package cloud.elsa.app.core.data.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CompletableDeferred

/**
 * Android 런타임 권한. 권한 창은 Activity 가 띄워야 해서 `MainActivity.onCreate` 가 [bind] 로 연결한다.
 *
 * "다시 묻지 않음" 판별: Android 는 그 상태를 직접 알려주지 않는다.
 * 거부됐고 + 설명이 필요하다고 하지 않고(`shouldShowRequestPermissionRationale == false`) + 전에 요청한 적이 있으면
 * 시스템 창이 더는 뜨지 않는 상태다. "전에 요청한 적"은 여기서 기록한다(SharedPreferences).
 */
class AndroidPermissionController(
    private val context: Context,
) : PermissionController {

    private var activity: ComponentActivity? = null
    private var launcher: ActivityResultLauncher<Array<String>>? = null

    /** 권한 창이 떠 있는 동안의 대기. 한 번에 하나만 요청한다 */
    private var pending: CompletableDeferred<Unit>? = null

    private val asked by lazy { context.getSharedPreferences("elsa.permissions", Context.MODE_PRIVATE) }

    /** Activity.onCreate(STARTED 전)에서 한 번 */
    fun bind(activity: ComponentActivity) {
        this.activity = activity
        launcher = activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            pending?.complete(Unit)
            pending = null
        }
        // Activity 가 끝나면 붙잡지 않는다. 떠 있던 요청은 끝낸다(호출한 쪽이 현재 상태를 다시 읽는다)
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                if (this@AndroidPermissionController.activity === activity) {
                    this@AndroidPermissionController.activity = null
                    launcher = null
                    pending?.complete(Unit)
                    pending = null
                }
            }
        })
    }

    override suspend fun status(permission: AppPermission): PermissionStatus {
        val names = permission.manifestNames()
        if (names.isEmpty() || names.any { isGranted(it) }) return PermissionStatus.Granted
        val activity = activity
        return when {
            activity != null && names.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) } -> PermissionStatus.Denied
            asked.getBoolean(permission.name, false) -> PermissionStatus.PermanentlyDenied
            else -> PermissionStatus.NotRequested
        }
    }

    override suspend fun request(permission: AppPermission): PermissionStatus {
        val current = status(permission)
        val launcher = launcher
        // 이미 허용됐거나, 창을 띄울 수 없거나(Activity 없음·다른 요청 진행 중), 더는 뜨지 않는 상태면 현재 상태 그대로
        if (current.isGranted || current == PermissionStatus.PermanentlyDenied || launcher == null || pending != null) return current

        val waiting = CompletableDeferred<Unit>()
        pending = waiting
        asked.edit().putBoolean(permission.name, true).apply()
        launcher.launch(permission.manifestNames().toTypedArray())
        waiting.await()
        return status(permission)
    }

    override fun openSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun isGranted(name: String) =
        ContextCompat.checkSelfPermission(context, name) == PackageManager.PERMISSION_GRANTED

    /** 빈 목록 = 이 OS 버전에서는 런타임 권한이 아니다(항상 허용) */
    private fun AppPermission.manifestNames(): List<String> = when (this) {
        AppPermission.Notifications ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()
        AppPermission.Camera -> listOf(Manifest.permission.CAMERA)
        // 대략 위치만 허용해도 허용으로 본다
        AppPermission.Location -> listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    }
}
