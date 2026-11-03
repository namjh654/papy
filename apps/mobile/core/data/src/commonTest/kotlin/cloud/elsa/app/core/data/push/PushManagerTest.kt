package cloud.elsa.app.core.data.push

import cloud.elsa.app.core.common.navigation.DeepLink
import cloud.elsa.app.core.data.session.AuthStore
import cloud.elsa.app.core.data.storage.FakeLocalStorage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class PushManagerTest {

    private val storage = FakeLocalStorage()

    private fun manager(scope: kotlinx.coroutines.CoroutineScope) = PushManager(
        repository = PushRepository(
            api = object : PushTokenApi {
                override suspend fun register(request: PushTokenRequest) = Unit
                override suspend fun unregister(token: String) = Unit
            },
            storage = storage,
            provider = NoOpPushProvider,
            authStore = AuthStore(),
        ),
        provider = NoOpPushProvider,
        scope = scope,
    )

    @Test
    fun openedNotificationKeepsDeepLinkUntilConsumed() = runTest {
        val push = manager(this)

        push.onNotificationOpened(mapOf("screen" to "example", "id" to "3"))
        assertEquals(DeepLink("example", mapOf("id" to "3")), push.pendingDeepLink.value)

        push.consumeDeepLink()
        assertNull(push.pendingDeepLink.value)

        push.onNotificationOpened(mapOf("title" to "no link"))
        assertNull(push.pendingDeepLink.value)
    }

    @Test
    fun openLinkKeepsDeepLinkLikeNotification() = runTest {
        val manager = manager(backgroundScope)

        assertEquals(true, manager.openLink("elsa://notice/3"))
        assertEquals("notice/3", manager.pendingDeepLink.value?.path)
        assertEquals(false, manager.openLink("elsa://"))
    }

    @Test
    fun tokenAndPermissionAreForwarded() = runTest(UnconfinedTestDispatcher()) {
        val push = manager(this)

        push.onNewToken("t1")
        push.onPermissionChanged(PushPermission.Granted)

        assertEquals("t1", storage.getString("push.token"))
        assertEquals(PushPermission.Granted, push.permission.value)
    }
}
