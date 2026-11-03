package io.github.namjh654.papy.push

import android.content.Context
import io.github.namjh654.papy.core.data.push.NoOpPushProvider
import io.github.namjh654.papy.core.data.push.PushManager
import io.github.namjh654.papy.core.data.push.PushProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging

/**
 * FCM 푸시 제공자(기본). `androidApp/google-services.json` 이 있어 Firebase 가 초기화됐을 때만 쓴다.
 * 다른 제공자로 바꾸려면 [PushProvider] 를 구현해 PapyApplication 에서 넘긴다.
 */
class FcmPushProvider private constructor() : PushProvider {

    override val id: String = "fcm"
    override val isEnabled: Boolean = true

    /** 현재 토큰을 받아 KMP 에 알린다. 이후 갱신은 [PapyMessagingService.onNewToken] */
    fun start(pushManager: PushManager) {
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token -> pushManager.onNewToken(token) }
    }

    override fun deleteToken() {
        FirebaseMessaging.getInstance().deleteToken()
    }

    companion object {
        /** Firebase 설정이 없으면 [NoOpPushProvider] */
        fun createOrNoOp(context: Context): PushProvider =
            if (FirebaseApp.getApps(context).isNotEmpty()) FcmPushProvider() else NoOpPushProvider
    }
}
