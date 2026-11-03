package io.github.namjh654.papy.core.data.social

import io.github.namjh654.papy.core.data.session.UserModel
import io.github.namjh654.papy.core.data.session.UserStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SocialAuthTest {

    private fun available(platform: String, mock: Boolean = false, kakao: String = "", googleWeb: String = "", googleIos: String = "", apple: Boolean = false, naver: String = "") =
        SocialConfig.available(platform, mock, kakao, googleWeb, googleIos, apple, naver)

    @Test
    fun noKeysNoButtons() {
        assertEquals(emptyList(), available("android"))
        assertEquals(emptyList(), available("ios", apple = false))
    }

    @Test
    fun keysDecideProvidersPerPlatform() {
        assertEquals(listOf(SocialProvider.Kakao, SocialProvider.Google, SocialProvider.Naver), available("android", kakao = "k", googleWeb = "w", naver = "n"))
        // 구글은 플랫폼별 클라이언트 ID, 애플은 iOS 만
        assertEquals(listOf(SocialProvider.Kakao), available("android", kakao = "k", googleIos = "i", apple = true))
        assertEquals(listOf(SocialProvider.Google, SocialProvider.Apple), available("ios", googleIos = "i", googleWeb = "w", apple = true))
    }

    @Test
    fun mockShowsEveryProviderOfThePlatform() {
        assertEquals(listOf(SocialProvider.Kakao, SocialProvider.Google, SocialProvider.Naver), available("android", mock = true))
        assertEquals(SocialProvider.entries, available("ios", mock = true))
    }

    @Test
    fun logoutSignsOutOnlyTheSocialProviderInUse() = runTest {
        val signedOut = mutableListOf<SocialProvider>()
        val provider = object : SocialAuthProvider {
            override fun signOut(provider: SocialProvider) { signedOut += provider }
            override fun unlink(provider: SocialProvider) = Unit
        }
        val store = UserStore()
        val cleaner = SocialSessionCleaner(provider, store)

        store.setUser(UserModel(id = "1", name = "id user"))
        cleaner.onSessionEnding()
        store.setUser(UserModel(id = "2", name = "naver user", provider = SocialProvider.Naver))
        cleaner.onSessionEnding()

        assertEquals(listOf(SocialProvider.Naver), signedOut)
    }
}
