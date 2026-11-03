package cloud.elsa.app.feature.settings.profile

import cloud.elsa.app.core.common.error.AppError
import cloud.elsa.app.core.common.error.ErrorCode
import cloud.elsa.app.core.data.session.AuthStore
import cloud.elsa.app.core.data.session.SessionRepository
import cloud.elsa.app.core.data.session.UserModel
import cloud.elsa.app.core.data.session.UserStore
import cloud.elsa.app.core.data.storage.login.LoginStorage
import cloud.elsa.app.core.data.storage.token.TokenStorage
import cloud.elsa.app.core.data.storage.user.UserStorage
import cloud.elsa.app.feature.settings.MemorySecureStorage
import cloud.elsa.app.feature.settings.MemoryStorage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 로그인 사용자 + 프로필 API 대역 */
internal class ProfileFixture {
    val storage = MemoryStorage()
    val userStorage = UserStorage(storage)
    val userStore = UserStore()
    val session = SessionRepository(
        TokenStorage(MemorySecureStorage(), storage), userStorage, LoginStorage(storage), AuthStore(), userStore,
    )
    val user = UserModel(id = "elsa", name = "Elsa", email = "elsa@example.com", avatarUrl = "https://cdn/old.jpg")

    var error: Throwable? = null
    val uploads = mutableListOf<ByteArray>()
    val updates = mutableListOf<ProfileUpdateRequest>()
    val api = object : ProfileApi {
        override suspend fun uploadAvatar(bytes: ByteArray): AvatarResponse {
            error?.let { throw it }
            uploads += bytes
            return AvatarResponse("https://cdn/new.jpg")
        }

        override suspend fun update(request: ProfileUpdateRequest): UserModel {
            error?.let { throw it }
            updates += request
            // 서버가 id·사진 주소 없이 돌려주는 경우
            return UserModel(id = "", name = request.name, email = request.email)
        }
    }
    val repository = ProfileRepository(api, session)

    init {
        userStore.setUser(user)
    }
}

class ProfileRepositoryTest {

    private val fixture = ProfileFixture()

    @Test
    fun validatesBeforeCallingApi() = runTest {
        val name = assertFailsWith<AppError.Validation> { fixture.repository.save(fixture.user, " ", "", null) }
        val email = assertFailsWith<AppError.Validation> { fixture.repository.save(fixture.user, "Elsa", "not-an-email", null) }

        assertEquals(ErrorCode.NameRequired, name.code)
        assertEquals(ErrorCode.InvalidEmail, email.code)
        assertTrue(fixture.updates.isEmpty())
    }

    @Test
    fun savesWithoutNewAvatarAndKeepsOldPhoto() = runTest {
        val saved = fixture.repository.save(fixture.user, " Anna ", "", avatar = null)

        assertTrue(fixture.uploads.isEmpty())
        assertEquals(ProfileUpdateRequest("Anna", email = null, avatarUrl = "https://cdn/old.jpg"), fixture.updates.single())
        assertEquals(UserModel(id = "elsa", name = "Anna", email = null, avatarUrl = "https://cdn/old.jpg"), saved)
    }

    @Test
    fun uploadsNewAvatarThenUpdatesSession() = runTest {
        val saved = fixture.repository.save(fixture.user, "Elsa", "elsa@example.com", avatar = byteArrayOf(1, 2, 3))

        assertEquals(3, fixture.uploads.single().size)
        assertEquals("https://cdn/new.jpg", fixture.updates.single().avatarUrl)
        assertEquals("https://cdn/new.jpg", saved.avatarUrl)
        // 세션(메모리·저장소)이 함께 바뀐다 → 홈·설정이 따라온다
        assertEquals(saved, fixture.userStore.user.value)
        assertEquals(saved, fixture.userStorage.getUser())
    }

    @Test
    fun serverFailureLeavesSessionUntouched() = runTest {
        fixture.error = AppError.Http(500, "")

        assertFailsWith<AppError.Http> { fixture.repository.save(fixture.user, "Anna", "", avatar = null) }

        assertEquals(fixture.user, fixture.userStore.user.value)
    }
}
