package cloud.elsa.app.core.data.update

import cloud.elsa.app.core.common.error.AppError
import cloud.elsa.app.core.data.storage.FakeLocalStorage
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppUpdateManagerTest {

    private val storage = FakeLocalStorage()

    private fun TestScope.manager(api: AppVersionApi?, current: String = "1.2.0") =
        AppUpdateManager(api, storage, currentVersion = current, platform = "android", scope = this)

    private fun api(response: AppVersionResponse) = SampleAppVersionApi(response)

    @Test
    fun belowMinimumIsRequired() = runTest {
        val m = manager(api(AppVersionResponse(minimumVersion = "1.3.0", latestVersion = "2.0.0", storeUrl = "s")))
        m.check()
        testScheduler.advanceUntilIdle()
        assertEquals(AppUpdateStatus.Required("1.3.0", "s"), m.status.value)
    }

    @Test
    fun minorOrMajorBehindLatestIsRequired() = runTest {
        val minor = manager(api(AppVersionResponse(latestVersion = "1.3.0", storeUrl = "s")))
        minor.check()
        testScheduler.advanceUntilIdle()
        assertEquals(AppUpdateStatus.Required("1.3.0", "s"), minor.status.value)

        val major = manager(api(AppVersionResponse(latestVersion = "2.0.0")))
        major.check()
        testScheduler.advanceUntilIdle()
        assertEquals(AppUpdateStatus.Required("2.0.0", null), major.status.value)
    }

    @Test
    fun aheadOfServerIsUpToDate() = runTest {
        val m = manager(api(AppVersionResponse(latestVersion = "1.1.9")))
        m.check()
        testScheduler.advanceUntilIdle()
        assertEquals(AppUpdateStatus.UpToDate, m.status.value)
    }

    @Test
    fun noticeShowsUntilConfirmedButMaintenanceStays() = runTest {
        val notice = AppNotice(id = "n1", message = "hello")
        val m = manager(api(AppVersionResponse(notice = notice)))
        m.check()
        testScheduler.advanceUntilIdle()
        assertEquals(notice, m.notice.value)

        m.dismissNotice()
        testScheduler.advanceUntilIdle()
        m.check() // 확인한 공지는 다시 보이지 않는다
        testScheduler.advanceUntilIdle()
        assertEquals(null, m.notice.value)

        val maintenance = AppNotice(id = "n1", message = "점검", blocking = true)
        val blocked = manager(api(AppVersionResponse(notice = maintenance)))
        blocked.check()
        testScheduler.advanceUntilIdle()
        blocked.dismissNotice() // 점검은 닫히지 않는다(같은 id 를 확인했어도 보인다)
        assertEquals(maintenance, blocked.notice.value)
    }

    @Test
    fun patchBehindLatestIsOptionalUntilDismissed() = runTest {
        val m = manager(api(AppVersionResponse(minimumVersion = "1.0.0", latestVersion = "1.2.1")))
        m.check()
        testScheduler.advanceUntilIdle()
        assertEquals(AppUpdateStatus.Optional("1.2.1", null), m.status.value)

        m.dismissOptional()
        testScheduler.advanceUntilIdle()
        assertEquals(AppUpdateStatus.UpToDate, m.status.value)

        m.check() // 같은 최신 버전은 다시 묻지 않는다
        testScheduler.advanceUntilIdle()
        assertEquals(AppUpdateStatus.UpToDate, m.status.value)
    }

    @Test
    fun failureOrNoApiStaysUpToDate() = runTest {
        val failing = object : AppVersionApi {
            override suspend fun version(platform: String): AppVersionResponse = throw AppError.Http(404, "")
        }
        manager(failing).apply { check(); testScheduler.advanceUntilIdle(); assertEquals(AppUpdateStatus.UpToDate, status.value) }
        manager(null).apply { check(); testScheduler.advanceUntilIdle(); assertEquals(AppUpdateStatus.UpToDate, status.value) }
    }

    @Test
    fun patchOnlyDifferenceIsNotBeyondPatch() {
        assertTrue(!AppUpdateManager.differsBeyondPatch("1.2.0", "1.2.7"))
        assertTrue(AppUpdateManager.differsBeyondPatch("1.2.9", "1.3.0"))
        assertTrue(AppUpdateManager.differsBeyondPatch("1.9.9", "2.0"))
        assertTrue(!AppUpdateManager.differsBeyondPatch("1.2.0-dev", "1.2.3"))
    }

    @Test
    fun comparesVersionsNumerically() {
        assertTrue(AppUpdateManager.compareVersions("1.2.10", "1.2.9") > 0)
        assertTrue(AppUpdateManager.compareVersions("1.2", "1.2.0") == 0)
        assertTrue(AppUpdateManager.compareVersions("1.0.0-dev", "1.0.1") < 0)
    }

    @Test
    fun featuresGoToFeatureFlags() = runTest {
        val flags = cloud.elsa.app.core.data.flags.FeatureFlags(storage, backgroundScope)
        val m = AppUpdateManager(api(AppVersionResponse(features = mapOf("chat_attachments" to false))), storage, "1.2.0", "ios", this, flags)
        m.check()
        testScheduler.advanceUntilIdle()
        assertTrue(!flags.isEnabled(cloud.elsa.app.core.data.flags.FeatureKey.ChatAttachments))
    }
}
