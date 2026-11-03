package io.github.namjh654.papy.core.data.platform

import io.github.namjh654.papy.core.data.push.devicePlatform

/**
 * 플랫폼에 따라 달라야 하는 **동작 규칙**에만 쓴다(예: 시스템 뒤로가기 버튼이 있는 Android 의 종료 재확인).
 * 화면 모양·환경 값 분기에는 쓰지 않는다.
 */
object DevicePlatform {
    /** 시스템 뒤로가기(버튼·제스처)로 앱을 나갈 수 있는 플랫폼 */
    val hasSystemBack: Boolean get() = devicePlatform == "android"
}
