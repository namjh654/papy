package io.github.namjh654.papy.core.data.session

/**
 * 세션 시작/종료에 맞춰 할 일이 있는 기능(푸시 토큰 등록 등)이 구현해 [SessionRepository] 에 넘긴다.
 * 리스너가 실패해도 세션 흐름(로그인·로그아웃)은 계속된다.
 */
interface SessionListener {
    /** 로그인 성공·세션 복원 뒤 (인증 상태) */
    suspend fun onSessionStarted() {}

    /** 로그아웃 직전 (아직 토큰이 있어 서버 호출 가능) */
    suspend fun onSessionEnding() {}
}
