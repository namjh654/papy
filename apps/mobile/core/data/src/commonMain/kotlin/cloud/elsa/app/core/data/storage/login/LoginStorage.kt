package cloud.elsa.app.core.data.storage.login

import cloud.elsa.app.core.data.storage.LocalStorage
import cloud.elsa.app.core.data.storage.getBoolean
import cloud.elsa.app.core.data.storage.setBoolean
import cloud.elsa.app.core.data.storage.setStringOrRemove

class LoginStorage(
    private val localStorage: LocalStorage
) {

    suspend fun setRememberId(enable: Boolean) {
        localStorage.setBoolean(LoginStorageKey.REMEMBER_ID, enable)
    }

    suspend fun isRememberId(): Boolean {
        return localStorage.getBoolean(LoginStorageKey.REMEMBER_ID) ?: false
    }

    suspend fun setAutoLogin(enable: Boolean) {
        localStorage.setBoolean(LoginStorageKey.AUTO_LOGIN, enable)
    }

    suspend fun isAutoLogin(): Boolean {
        return localStorage.getBoolean(LoginStorageKey.AUTO_LOGIN) ?: false
    }

    suspend fun setSavedId(id: String?) {
        localStorage.setStringOrRemove(LoginStorageKey.SAVED_ID, id)
    }

    suspend fun getSavedId(): String? {
        return localStorage.getString(LoginStorageKey.SAVED_ID)
    }

    suspend fun clear() {
        localStorage.remove(LoginStorageKey.REMEMBER_ID)
        localStorage.remove(LoginStorageKey.AUTO_LOGIN)
        localStorage.remove(LoginStorageKey.SAVED_ID)
    }
}
