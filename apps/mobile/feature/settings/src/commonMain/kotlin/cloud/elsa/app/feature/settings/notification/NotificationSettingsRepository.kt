package cloud.elsa.app.feature.settings.notification

/** 알림 설정 읽기·바꾸기. 한 종류만 바꿔도 네 값을 함께 보낸다(서버가 통째로 저장) */
class NotificationSettingsRepository(
    private val api: NotificationSettingsApi,
) {
    suspend fun load(): NotificationPreferences = api.preferences()

    suspend fun set(current: NotificationPreferences, category: NotificationCategory, enabled: Boolean): NotificationPreferences =
        api.update(current.with(category, enabled).toRequest())
}

internal fun NotificationPreferences.with(category: NotificationCategory, enabled: Boolean): NotificationPreferences = when (category) {
    NotificationCategory.Chat -> copy(chat = enabled)
    NotificationCategory.Comment -> copy(comment = enabled)
    NotificationCategory.Notice -> copy(notice = enabled)
    NotificationCategory.Marketing -> copy(marketing = enabled)
}

internal fun NotificationPreferences.toRequest() = NotificationPreferencesRequest(chat = chat, comment = comment, notice = notice, marketing = marketing)
