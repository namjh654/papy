package cloud.elsa.app.feature.settings.notification

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

internal class FakeNotificationSettingsApi : NotificationSettingsApi {
    var stored = NotificationPreferences()
    var error: Throwable? = null
    var updatedAt = 1_700_000_000_000L
    val requests = mutableListOf<NotificationPreferencesRequest>()

    override suspend fun preferences(): NotificationPreferences {
        error?.let { throw it }
        return stored
    }

    override suspend fun update(request: NotificationPreferencesRequest): NotificationPreferences {
        error?.let { throw it }
        requests += request
        val marketingChanged = request.marketing != stored.marketing
        stored = NotificationPreferences(request.chat, request.comment, request.notice, request.marketing, if (marketingChanged) updatedAt else stored.marketingUpdatedAt)
        return stored
    }
}

class NotificationSettingsRepositoryTest {

    private val api = FakeNotificationSettingsApi()
    private val repository = NotificationSettingsRepository(api)

    @Test
    fun changingOneCategorySendsAllFour() = runTest {
        val current = repository.load()
        val saved = repository.set(current, NotificationCategory.Comment, false)

        assertEquals(NotificationPreferencesRequest(chat = true, comment = false, notice = true, marketing = false), api.requests.single())
        assertEquals(false, saved.comment)
    }

    @Test
    fun marketingChangeReturnsServerTime() = runTest {
        val saved = repository.set(repository.load(), NotificationCategory.Marketing, true)
        assertEquals(true, saved.marketing)
        assertEquals(api.updatedAt, saved.marketingUpdatedAt)
    }
}
