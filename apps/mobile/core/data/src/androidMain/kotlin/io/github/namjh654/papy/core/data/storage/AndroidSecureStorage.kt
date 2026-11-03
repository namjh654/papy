package io.github.namjh654.papy.core.data.storage

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.first

/**
 * Android Keystore(AES-256-GCM) 로 암호화한 값을 전용 DataStore 에 저장하는 [SecureStorage].
 * 키는 기기 밖으로 나가지 않으므로 백업·다른 기기에서 복원된 값은 복호화되지 않고 null 이 된다.
 */
internal class AndroidSecureStorage(
    private val dataStore: DataStore<Preferences>,
    private val cipher: KeystoreCipher = KeystoreCipher(),
) : SecureStorage {

    override suspend fun getString(key: String): String? {
        val encrypted = dataStore.data.first()[stringPreferencesKey(key)] ?: return null
        return runCatching { cipher.decrypt(encrypted) }.getOrNull()
    }

    override suspend fun setString(key: String, value: String) {
        val encrypted = cipher.encrypt(value)
        dataStore.edit { it[stringPreferencesKey(key)] = encrypted }
    }

    override suspend fun remove(key: String) {
        dataStore.edit { it.remove(stringPreferencesKey(key)) }
    }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}

/** 저장 형식: Base64(IV 12바이트 + 암호문·태그) */
internal class KeystoreCipher(
    private val alias: String = "io.github.namjh654.papy.secure",
) {

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.iv + cipher.doFinal(plain.encodeToByteArray())
        return Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    fun decrypt(encoded: String): String {
        val sealed = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
        }
        return cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES).decodeToString()
    }

    @Synchronized
    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            generateKey()
        }
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
