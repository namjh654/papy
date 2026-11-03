package io.github.namjh654.papy.update

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * 스토어 업데이트 실행(Android 전용 인터페이스). KMP AppUpdateManager 가 업데이트가 필요하다고 판단하면 루트가 부른다.
 * iOS 는 App Store 링크를 연다(AppNavigation).
 */
interface StoreUpdater {
    /** @param required 강제 업데이트면 즉시(IMMEDIATE), 아니면 백그라운드(FLEXIBLE) */
    fun startUpdate(activity: Activity, required: Boolean, storeUrl: String?)
}

/**
 * Play In-App Update. Play 가 업데이트를 알고 있으면 앱 안에서 받고, 아니면(개발 빌드·다른 스토어) 스토어 페이지를 연다.
 * FLEXIBLE 은 다운로드가 끝나면 바로 설치(앱 재시작)한다.
 */
class PlayStoreUpdater : StoreUpdater {

    override fun startUpdate(activity: Activity, required: Boolean, storeUrl: String?) {
        val manager = AppUpdateManagerFactory.create(activity)
        val type = if (required) AppUpdateType.IMMEDIATE else AppUpdateType.FLEXIBLE
        manager.appUpdateInfo
            .addOnSuccessListener { info ->
                val available = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE ||
                    info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
                if (available && info.isUpdateTypeAllowed(type)) {
                    if (type == AppUpdateType.FLEXIBLE) {
                        manager.registerListener(object : InstallStateUpdatedListener {
                            override fun onStateUpdate(state: com.google.android.play.core.install.InstallState) {
                                if (state.installStatus() == InstallStatus.DOWNLOADED) {
                                    manager.unregisterListener(this)
                                    manager.completeUpdate()
                                }
                            }
                        })
                    }
                    manager.startUpdateFlow(info, activity, AppUpdateOptions.newBuilder(type).build())
                } else {
                    openStore(activity, storeUrl)
                }
            }
            .addOnFailureListener { openStore(activity, storeUrl) }
    }

    private fun openStore(activity: Activity, storeUrl: String?) {
        val url = storeUrl ?: "market://details?id=${activity.packageName}"
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${activity.packageName}")))
        }
    }
}
