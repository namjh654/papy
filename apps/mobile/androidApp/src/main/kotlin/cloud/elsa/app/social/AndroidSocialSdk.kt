package cloud.elsa.app.social

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import cloud.elsa.app.R
import cloud.elsa.app.core.data.config.AppConfig
import cloud.elsa.app.core.data.social.SocialAuthProvider
import cloud.elsa.app.core.data.social.SocialCredential
import cloud.elsa.app.core.data.social.SocialProvider
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.common.KakaoSdk
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import com.kakao.sdk.user.UserApiClient
import com.navercorp.nid.NidOAuth
import com.navercorp.nid.core.data.errorcode.NidOAuthErrorCode
import com.navercorp.nid.oauth.util.NidOAuthCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import kotlin.coroutines.resume

/** 플랫폼 SDK 인증 결과 → 화면이 `LoginViewModel` 의 해당 함수로 넘긴다 */
sealed interface SocialSignInResult {
    data class Success(val credential: SocialCredential) : SocialSignInResult
    data object Cancelled : SocialSignInResult
    data class Failed(val cause: Throwable?) : SocialSignInResult
}

/**
 * 카카오·구글·네이버 SDK(DEC-105~109, 119). 키는 env(`social.*`) → `AppConfig`. 키가 없는 제공자는 초기화하지 않고
 * KMP `SocialConfig` 가 버튼도 숨긴다. 애플 로그인은 Android 에서 제외(DEC-109).
 */
object AndroidSocialSdk {

    private const val TAG = "ElsaSocial"

    /** 앱 시작 때 한 번(ElsaApplication) */
    fun initialize(context: Context) {
        if (AppConfig.SOCIAL_KAKAO_APP_KEY.isNotBlank()) KakaoSdk.init(context, AppConfig.SOCIAL_KAKAO_APP_KEY)
        if (AppConfig.SOCIAL_NAVER_CLIENT_ID.isNotBlank()) {
            NidOAuth.initialize(
                context,
                AppConfig.SOCIAL_NAVER_CLIENT_ID,
                AppConfig.SOCIAL_NAVER_CLIENT_SECRET,
                context.getString(R.string.app_name),
            )
        }
    }

    suspend fun signIn(activity: Activity, provider: SocialProvider): SocialSignInResult = when (provider) {
        SocialProvider.Kakao -> kakao(activity)
        SocialProvider.Google -> google(activity)
        SocialProvider.Naver -> naver(activity)
        SocialProvider.Apple -> SocialSignInResult.Failed(UnsupportedOperationException("Android 는 애플 로그인을 쓰지 않는다(DEC-109)"))
    }.also { if (it is SocialSignInResult.Failed) Log.w(TAG, "$provider 로그인 실패", it.cause) }

    // ------------------------------------------------------------ 카카오

    /** 카카오톡이 있으면 카카오톡으로, 없거나 카카오톡 쪽 오류면 카카오계정(웹)으로. 사용자가 취소하면 넘기지 않는다(카카오 가이드) */
    private suspend fun kakao(activity: Activity): SocialSignInResult {
        val client = UserApiClient.instance
        if (client.isKakaoTalkLoginAvailable(activity)) {
            val talk = kakaoLogin { callback -> client.loginWithKakaoTalk(activity, callback = callback) }
            if (talk !is SocialSignInResult.Failed) return talk
        }
        return kakaoLogin { callback -> client.loginWithKakaoAccount(activity, callback = callback) }
    }

    private suspend fun kakaoLogin(start: ((OAuthToken?, Throwable?) -> Unit) -> Unit): SocialSignInResult =
        suspendCancellableCoroutine { continuation ->
            start { token, error ->
                val result = when {
                    token != null -> SocialSignInResult.Success(SocialCredential(SocialProvider.Kakao, token.accessToken))
                    error is ClientError && error.reason == ClientErrorCause.Cancelled -> SocialSignInResult.Cancelled
                    else -> SocialSignInResult.Failed(error)
                }
                if (continuation.isActive) continuation.resume(result)
            }
        }

    // ------------------------------------------------------------ 구글 (Credential Manager)

    private suspend fun google(activity: Activity): SocialSignInResult {
        val nonce = UUID.randomUUID().toString()
        val option = GetSignInWithGoogleOption.Builder(AppConfig.SOCIAL_GOOGLE_WEB_CLIENT_ID).setNonce(nonce).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                SocialSignInResult.Success(SocialCredential(SocialProvider.Google, idToken, nonce = nonce))
            } else {
                SocialSignInResult.Failed(IllegalStateException("예상하지 못한 자격 증명: ${credential.type}"))
            }
        } catch (_: GetCredentialCancellationException) {
            SocialSignInResult.Cancelled
        } catch (e: GetCredentialException) {
            SocialSignInResult.Failed(e)
        } catch (e: GoogleIdTokenParsingException) {
            SocialSignInResult.Failed(e)
        }
    }

    // ------------------------------------------------------------ 네이버 (NidOAuth 5.x)

    private suspend fun naver(activity: Activity): SocialSignInResult = suspendCancellableCoroutine { continuation ->
        NidOAuth.requestLogin(activity, object : NidOAuthCallback {
            override fun onSuccess() {
                val token = NidOAuth.getAccessToken()
                val result = if (token.isNullOrBlank()) {
                    SocialSignInResult.Failed(IllegalStateException("네이버 토큰 없음"))
                } else {
                    SocialSignInResult.Success(SocialCredential(SocialProvider.Naver, token))
                }
                if (continuation.isActive) continuation.resume(result)
            }

            override fun onFailure(errorCode: String, errorDescription: String) {
                val result = if (NidOAuth.getLastErrorCode() == NidOAuthErrorCode.CLIENT_USER_CANCEL) {
                    SocialSignInResult.Cancelled
                } else {
                    SocialSignInResult.Failed(IllegalStateException("$errorCode $errorDescription"))
                }
                if (continuation.isActive) continuation.resume(result)
            }
        })
    }
}

/** 로그아웃 직전 SDK 세션 정리(KMP `SocialSessionCleaner`)·탈퇴 뒤 연결 끊기 */
class AndroidSocialAuthProvider(private val context: Context) : SocialAuthProvider {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun signOut(provider: SocialProvider) {
        when (provider) {
            SocialProvider.Kakao -> if (AppConfig.SOCIAL_KAKAO_APP_KEY.isNotBlank()) UserApiClient.instance.logout { }
            SocialProvider.Naver -> if (NidOAuth.isInitialized()) {
                NidOAuth.logout(object : NidOAuthCallback {
                    override fun onSuccess() = Unit
                    override fun onFailure(errorCode: String, errorDescription: String) = Unit
                })
            }
            SocialProvider.Google -> scope.launch {
                runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
            }
            SocialProvider.Apple -> Unit
        }
    }

    /** 회원 탈퇴 뒤 제공자와의 앱 연결 끊기(KMP `WithdrawRepository` 가 부른다). 실패해도 탈퇴는 이미 끝났으므로 무시한다 */
    override fun unlink(provider: SocialProvider) {
        when (provider) {
            SocialProvider.Kakao -> if (AppConfig.SOCIAL_KAKAO_APP_KEY.isNotBlank()) UserApiClient.instance.unlink { }
            SocialProvider.Naver -> if (NidOAuth.isInitialized()) {
                NidOAuth.disconnect(object : NidOAuthCallback {
                    override fun onSuccess() = Unit
                    override fun onFailure(errorCode: String, errorDescription: String) = Unit
                })
            }
            // Credential Manager 에는 앱 연결 해제 API 가 없다 — 저장된 자격 증명만 지운다(권한 회수는 서버가 토큰 폐기)
            SocialProvider.Google -> signOut(provider)
            SocialProvider.Apple -> Unit // 애플은 서버가 authorizationCode 로 토큰을 폐기한다
        }
    }
}
