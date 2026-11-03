package cloud.elsa.app.core.data.storage

/**
 * 민감한 값(토큰 등) 저장소 계약. [LocalStorage] 와 같은 문자열 Key-Value 이지만 플랫폼 보안 저장소를 쓴다.
 * - Android: Android Keystore AES-GCM 키로 암호화해 별도 DataStore 파일(`elsa.secure.preferences_pb`)에 저장
 * - iOS: Keychain (kSecClassGenericPassword, 이 기기에서만·첫 잠금 해제 후 접근)
 *
 * 복호화할 수 없는 값(백업 복원으로 키가 없는 경우 등)은 null 로 취급한다 → 재로그인.
 */
interface SecureStorage : LocalStorage
