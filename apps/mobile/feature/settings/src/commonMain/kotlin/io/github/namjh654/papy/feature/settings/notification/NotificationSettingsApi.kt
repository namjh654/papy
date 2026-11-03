package io.github.namjh654.papy.feature.settings.notification

import io.github.namjh654.papy.core.data.network.BaseApi
import kotlinx.serialization.Serializable

/**
 * 알림 종류별 받기(26-2). 서버가 보낼 때 이 값을 보고 거른다 — 앱은 고르기만 한다.
 * [marketingUpdatedAt]: 광고성 알림 동의·철회 시각(epoch 밀리초, 서버가 정한다) — 바꾸면 날짜를 안내해야 한다(정보통신망법 제50조).
 * Swift: `NotificationPreferences`
 */
@Serializable
data class NotificationPreferences(
    val chat: Boolean = true,
    val comment: Boolean = true,
    val notice: Boolean = true,
    val marketing: Boolean = false,
    val marketingUpdatedAt: Long? = null,
)

@Serializable
data class NotificationPreferencesRequest(
    val chat: Boolean,
    val comment: Boolean,
    val notice: Boolean,
    val marketing: Boolean,
)

/** 알림 설정 종류. Swift: `NotificationCategory.chat` 등 — 문구는 플랫폼 리소스 */
enum class NotificationCategory { Chat, Comment, Notice, Marketing }

/** 알림 설정 API 계약(가정): `GET /api/push/preferences` → [NotificationPreferences], `PUT` 같은 경로 {chat, comment, notice, marketing} → 바뀐 값 */
interface NotificationSettingsApi {
    suspend fun preferences(): NotificationPreferences
    suspend fun update(request: NotificationPreferencesRequest): NotificationPreferences
}

internal class RemoteNotificationSettingsApi(
    private val baseApi: BaseApi,
) : NotificationSettingsApi {
    override suspend fun preferences(): NotificationPreferences = baseApi.get("/api/push/preferences")

    override suspend fun update(request: NotificationPreferencesRequest): NotificationPreferences =
        baseApi.put("/api/push/preferences", body = request)
}
