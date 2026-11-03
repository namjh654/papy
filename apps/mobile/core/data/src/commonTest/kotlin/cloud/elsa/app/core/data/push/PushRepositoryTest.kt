package cloud.elsa.app.core.data.push

import cloud.elsa.app.core.common.error.AppError
import cloud.elsa.app.core.common.navigation.DeepLink
import cloud.elsa.app.core.data.session.AuthSession
import cloud.elsa.app.core.data.session.AuthStore
import cloud.elsa.app.core.data.session.SessionRepository
import cloud.elsa.app.core.data.session.UserModel
import cloud.elsa.app.core.data.session.UserStore
import cloud.elsa.app.core.data.storage.FakeLocalStorage
import cloud.elsa.app.core.data.storage.FakeSecureStorage
import cloud.elsa.app.core.data.storage.login.LoginStorage
import cloud.elsa.app.core.data.storage.token.TokenStorage
import cloud.elsa.app.core.data.storage.user.UserStorage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class RecordingPushApi : PushTokenApi {
    val calls = mutableListOf<String>()
    var accessTokenAtCall: (() -> String?)? = null
    var failing = false
    override suspend fun register(request: PushTokenRequest) {
        calls += "register:${request.token}:${request.provider}"
        if (failing) throw AppError.Http(404, "")
    }
    override suspend fun unregister(token: String) {
        calls += "unregister:$token:auth=${accessTokenAtCall?.invoke()}"
        if (failing) throw AppError.Http(404, "")
    }
}

private class FakeProvider : PushProvider {
    override val id = "fcm"
    override val isEnabled = true
    var deleted = 0
    override fun deleteToken() { deleted++ }
}

class PushRepositoryTest {

    private val storage = FakeLocalStorage()
    private val tokens = TokenStorage(FakeSecureStorage(), storage)
    private val authStore = AuthStore()
    private val api = RecordingPushApi()
    private val provider = FakeProvider()
    private val push = PushRepository(api, storage, provider, authStore)
    private val session = SessionRepository(
        tokens, UserStorage(storage), LoginStorage(storage), authStore, UserStore(), listeners = listOf(push),
    )
    private val user = UserModel(id = "elsa", name = "Elsa")

    @Test
    fun tokenBeforeLoginIsStoredAndRegisteredOnLogin() = runTest {
        push.onNewToken("t1")
        assertTrue(api.calls.isEmpty())

        session.start(AuthSession("access", null, user))

        assertEquals(listOf("register:t1:fcm"), api.calls)
    }

    @Test
    fun rotatedTokenWhileLoggedInReplacesOldOne() = runTest {
        push.onNewToken("t1")
        session.start(AuthSession("access", null, user))
        api.calls.clear()

        push.onNewToken("t2")
        push.onNewToken("t2") // 같은 토큰은 무시

        assertEquals(listOf("unregister:t1:auth=null", "register:t2:fcm"), api.calls)
    }

    @Test
    fun logoutUnregistersWhileStillAuthenticatedThenDeletesToken() = runTest {
        api.accessTokenAtCall = { null }
        push.onNewToken("t1")
        session.start(AuthSession("access", null, user))
        api.calls.clear()
        var accessToken: String? = null
        api.accessTokenAtCall = { accessToken }
        accessToken = tokens.getAccessToken()

        session.logout()

        assertEquals(listOf("unregister:t1:auth=access"), api.calls)
        assertEquals(1, provider.deleted)
        assertNull(push.token())
    }

    @Test
    fun serverFailureDoesNotBreakSession() = runTest {
        api.failing = true
        push.onNewToken("t1")

        session.start(AuthSession("access", null, user))
        session.logout()

        assertEquals(1, provider.deleted)
    }
}

class PushMessageParserTest {

    @Test
    fun linkWinsOverScreen() {
        val message = PushMessageParser.parse(mapOf("link" to "elsa://example?id=1", "screen" to "home"))
        assertEquals(DeepLink("example", mapOf("id" to "1")), message.deepLink)
    }

    @Test
    fun screenUsesRemainingKeysAsParams() {
        val message = PushMessageParser.parse(
            mapOf("screen" to "example", "id" to "9", "title" to "T", "google.message_id" to "x", "from" to "y"),
        )
        assertEquals(DeepLink("example", mapOf("id" to "9")), message.deepLink)
        assertEquals("T", message.title)
        assertEquals(mapOf("screen" to "example", "id" to "9", "title" to "T"), message.data)
    }

    @Test
    fun notificationFieldsWinAndNoLinkIsNull() {
        val message = PushMessageParser.parse(mapOf("title" to "data"), title = "notif", body = "b")
        assertEquals("notif", message.title)
        assertEquals("b", message.body)
        assertNull(message.deepLink)
    }
}
