package io.github.namjh654.papy.feature.auth.social

import io.github.namjh654.papy.core.data.session.SessionRepository

/**
 * 소셜 신규 가입의 마지막 단계. 세션은 로그인 때 이미 시작됐다 — 약관에 동의하면 가입을 마치고,
 * 동의하지 않고 나가면 로그아웃해 가입하지 않은 상태로 둔다(서버가 동의 전 계정을 정리한다고 가정).
 */
class SocialConsentRepository(
    private val api: SocialConsentApi,
    private val sessionRepository: SessionRepository,
) {
    suspend fun agree(marketing: Boolean) = api.agree(SocialConsentRequest(marketingAgreed = marketing))

    suspend fun cancel() = sessionRepository.logout()
}
