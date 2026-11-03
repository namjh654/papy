package io.github.namjh654.papy.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * 본인 확인(26-1): 지문·얼굴, 안 되면 기기 PIN·패턴(시스템 화면). iOS `DeviceAuth`(LocalAuthentication).
 * BIOMETRIC_WEAK | DEVICE_CREDENTIAL 은 모든 API 수준에서 쓸 수 있는 조합이다(androidx.biometric 1.1.0).
 */
object DeviceAuth {

    private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    /** 기기에 잠금 수단(생체·PIN)이 있는지 — 없으면 설정의 앱 잠금 행을 숨긴다 */
    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    /** 실패·취소는 [onFailed](잠금 화면에 머문다) */
    fun authenticate(activity: FragmentActivity, title: String, onSuccess: () -> Unit, onFailed: () -> Unit = {}) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFailed()
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build(),
        )
    }
}
