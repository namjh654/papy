package cloud.elsa.app.core.data.storage.user

import cloud.elsa.app.core.data.serialization.AppJson
import cloud.elsa.app.core.data.session.UserModel
import cloud.elsa.app.core.data.storage.LocalStorage
import kotlinx.serialization.json.Json

/** 로그인 사용자 정보를 JSON으로 직렬화해 영속화합니다. */
class UserStorage(
    private val localStorage: LocalStorage,
    private val json: Json = AppJson,
) {

    suspend fun setUser(user: UserModel) {
        localStorage.setString(
            UserStorageKey.USER,
            json.encodeToString(UserModel.serializer(), user),
        )
    }

    /** 저장 형식이 깨졌으면 null로 취급해 재로그인을 유도합니다. */
    suspend fun getUser(): UserModel? {
        val raw = localStorage.getString(UserStorageKey.USER) ?: return null
        return runCatching { json.decodeFromString(UserModel.serializer(), raw) }.getOrNull()
    }

    suspend fun clear() {
        localStorage.remove(UserStorageKey.USER)
    }
}
