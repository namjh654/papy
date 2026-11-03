package cloud.elsa.app.feature.auth.social

import cloud.elsa.app.core.data.session.AuthSession
import cloud.elsa.app.core.data.session.AuthStatus
import cloud.elsa.app.core.data.session.UserModel
import cloud.elsa.app.feature.auth.TestSession
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SocialConsentRepositoryTest {
    private val session = TestSession()
    private val api = FakeConsentApi()
    private val repository = SocialConsentRepository(api, session.repository)

    @Test
    fun agreeSendsMarketingChoice() = runTest {
        repository.agree(marketing = true)
        assertEquals(listOf(SocialConsentRequest(marketingAgreed = true)), api.requests)
    }

    @Test
    fun cancelEndsTheSession() = runTest {
        session.repository.start(AuthSession("a", "r", UserModel(id = "s1", name = "n")))
        repository.cancel()
        assertEquals(AuthStatus.Unauthenticated, session.authStore.status.value)
    }
}
