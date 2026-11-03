package io.github.namjh654.papy.core.data.serialization

import kotlinx.serialization.json.Json

/**
 * 앱 전체 JSON 설정의 단일 정의. API 요청/응답(BaseApi·Ktor)과 로컬 저장(UserStorage)이 같이 쓴다.
 * - ignoreUnknownKeys: 서버가 필드를 추가해도 깨지지 않게
 * - isLenient: 따옴표 없는 값 등 느슨한 응답 허용
 * - encodeDefaults: 기본값 필드도 전송/저장
 */
val AppJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
}
