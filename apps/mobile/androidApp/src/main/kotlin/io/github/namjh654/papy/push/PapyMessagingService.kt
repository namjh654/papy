package io.github.namjh654.papy.push

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.namjh654.papy.R
import io.github.namjh654.papy.app.MainActivity
import io.github.namjh654.papy.core.data.push.PushManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.koin.android.ext.android.inject

/**
 * FCM 수신. 앱이 foreground 이거나 data-only 메시지면 여기서 알림을 직접 만든다.
 * (background 의 notification 메시지는 시스템이 표시하고, 탭하면 data 가 MainActivity extras 로 온다.)
 */
class PapyMessagingService : FirebaseMessagingService() {

    private val pushManager: PushManager by inject()

    override fun onNewToken(token: String) {
        pushManager.onNewToken(token)
    }

    override fun onMessageReceived(remote: RemoteMessage) {
        val message = pushManager.parse(remote.data, remote.notification?.title, remote.notification?.body)
        if (message.title == null && message.body == null) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= 33
        ) return

        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply { message.data.forEach { (key, value) -> putExtra(key, value) } }
        val contentIntent = PendingIntent.getActivity(
            this,
            remote.messageId?.hashCode() ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, NotificationChannels.DEFAULT)
            .setSmallIcon(R.drawable.papy_ic_notification)
            .setContentTitle(message.title)
            .setContentText(message.body)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        NotificationManagerCompat.from(this).notify(remote.messageId?.hashCode() ?: 0, notification)
    }
}
