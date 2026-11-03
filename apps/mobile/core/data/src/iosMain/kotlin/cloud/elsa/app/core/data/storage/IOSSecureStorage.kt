@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package cloud.elsa.app.core.data.storage

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.posix.memcpy

/**
 * Keychain(kSecClassGenericPassword) 기반 [SecureStorage].
 * service = [service], account = 키. 이 기기에서만, 첫 잠금 해제 이후 접근 가능.
 * 앱을 지워도 Keychain 값은 남지만 세션 복원은 NSUserDefaults 의 자동 로그인·사용자 정보가 있어야 하므로 재설치 시 복원되지 않는다.
 */
internal class IOSSecureStorage(
    private val service: String = DEFAULT_SERVICE,
) : SecureStorage {

    override suspend fun getString(key: String): String? = memScoped {
        val result = alloc<CFTypeRefVar>()
        val status = withQuery(
            account = key,
            extra = listOf(kSecReturnData to kCFBooleanTrue, kSecMatchLimit to kSecMatchLimitOne),
        ) { query -> SecItemCopyMatching(query, result.ptr) }
        if (status != errSecSuccess) return@memScoped null
        (CFBridgingRelease(result.value) as? NSData)?.toByteArray()?.decodeToString()
    }

    override suspend fun setString(key: String, value: String) {
        remove(key)
        withQuery(
            account = key,
            extra = listOf(
                kSecValueData to value.encodeToByteArray().toNSData(),
                kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
            ),
        ) { query -> SecItemAdd(query, null) }
    }

    override suspend fun remove(key: String) {
        withQuery(account = key) { query -> SecItemDelete(query) }
    }

    override suspend fun clear() {
        withQuery(account = null) { query -> SecItemDelete(query) }
    }

    /**
     * class·service(·account) + [extra] 로 질의 딕셔너리를 만들어 [block] 에 넘긴다.
     * extra 값이 CF 포인터(상수)면 그대로, Kotlin/ObjC 객체면 CFBridgingRetain 후 끝나면 해제한다.
     */
    private fun <T> withQuery(
        account: String?,
        extra: List<Pair<CFStringRef?, Any?>> = emptyList(),
        block: (CFDictionaryRef?) -> T,
    ): T {
        val retained = mutableListOf<CFTypeRef?>()
        val query = CFDictionaryCreateMutable(kCFAllocatorDefault, 0.convert(), null, null)

        fun put(key: CFStringRef?, value: Any?) {
            val ref: CFTypeRef? = when (value) {
                is CPointer<*> -> value // CF 상수(kSecClass… 등)는 그대로
                else -> CFBridgingRetain(value).also { retained += it }
            }
            CFDictionaryAddValue(query, key, ref)
        }

        put(kSecClass, kSecClassGenericPassword)
        put(kSecAttrService, service)
        if (account != null) put(kSecAttrAccount, account)
        extra.forEach { (key, value) -> put(key, value) }

        try {
            return block(query)
        } finally {
            retained.forEach { CFRelease(it) }
            CFRelease(query)
        }
    }

    companion object {
        const val DEFAULT_SERVICE = "cloud.elsa.app.secure"
    }
}

private fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.convert()) }

private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).apply { usePinned { memcpy(it.addressOf(0), bytes, length) } }
}
