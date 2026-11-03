package io.github.namjh654.papy.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.namjh654.papy.R

/** 알림 채널. 채널이 더 필요하면 여기 추가하고 서버는 FCM `android.notification.channel_id` 로 지정한다 */
object NotificationChannels {

    /** AndroidManifest 의 com.google.firebase.messaging.default_notification_channel_id 와 같은 값 */
    const val DEFAULT = "default"

    fun create(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(DEFAULT, context.getString(R.string.push_channel_default), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }
}

/** 알림 탭으로 열린 Intent 의 extras → 푸시 data (FCM 시스템 키는 PushMessageParser 가 버린다) */
fun Intent.pushData(): Map<String, String> {
    val extras = extras ?: return emptyMap()
    return extras.keySet().mapNotNull { key -> extras.getString(key)?.let { key to it } }.toMap()
}
