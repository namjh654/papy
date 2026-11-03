package cloud.elsa.app.core.data.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/** Preferences DataStore 기반 [LocalStorage] 구현입니다. */
internal class AndroidLocalStorage(
    private val dataStore: DataStore<Preferences>,
) : LocalStorage {

    override suspend fun getString(key: String): String? =
        dataStore.data.first()[stringPreferencesKey(key)]

    override suspend fun setString(key: String, value: String) {
        dataStore.edit { it[stringPreferencesKey(key)] = value }
    }

    override suspend fun remove(key: String) {
        dataStore.edit { it.remove(stringPreferencesKey(key)) }
    }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}
