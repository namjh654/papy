package io.github.namjh654.papy.core.data.push

import io.github.namjh654.papy.core.data.session.AuthStore
import io.github.namjh654.papy.core.data.session.SessionListener
import io.github.namjh654.papy.core.data.storage.LocalStorage
import kotlinx.coroutines.CancellationException

/**
 * 푸시 토큰 보관과 서버 등록/해제.
 * - 토큰은 받는 즉시 저장하고, 로그인 상태면 바로 등록한다(갱신되면 이전 토큰 해제)
 * - 로그인·세션 복원([onSessionStarted]) 때 저장된 토큰을 등록한다
 * - 로그아웃([onSessionEnding], 인증이 아직 남아 있을 때) 서버에서 해제하고 제공자 토큰을 지운다
 * 서버 호출 실패는 무시한다(푸시 때문에 로그인·로그아웃이 막히지 않게). 다음 로그인·토큰 갱신 때 다시 등록된다.
 */
class PushRepository(
    private val api: PushTokenApi,
    private val storage: LocalStorage,
    private val provider: PushProvider,
    private val authStore: AuthStore,
) : SessionListener {

    suspend fun token(): String? = storage.getString(KEY_TOKEN)

    suspend fun onNewToken(token: String) {
        if (token.isBlank()) return
        val previous = token()
        if (previous == token) return
        storage.setString(KEY_TOKEN, token)
        if (authStore.isLoggedIn) {
            previous?.let { quietly { api.unregister(it) } }
            register(token)
        }
    }

    override suspend fun onSessionStarted() {
        token()?.let { register(it) }
    }

    override suspend fun onSessionEnding() {
        token()?.let { quietly { api.unregister(it) } }
        storage.remove(KEY_TOKEN)
        provider.deleteToken()
    }

    private suspend fun register(token: String) = quietly {
        api.register(PushTokenRequest(token = token, platform = devicePlatform, provider = provider.id))
    }

    private suspend fun quietly(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // 서버 미구현·네트워크 오류 등 — 다음 기회에 다시 등록
        }
    }

    private companion object {
        const val KEY_TOKEN = "push.token"
    }
}

/** 서버에 보내는 플랫폼 이름: "android" | "ios" (푸시 등록·업데이트 확인·문의) */
expect val devicePlatform: String
