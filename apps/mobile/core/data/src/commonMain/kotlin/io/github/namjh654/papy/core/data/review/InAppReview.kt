package io.github.namjh654.papy.core.data.review

/**
 * 스토어 리뷰 요청. Android 는 Play In-App Review, iOS 는 StoreKit `AppStore.requestReview`.
 * 표시 여부·횟수는 OS 가 정한다(요청해도 안 뜰 수 있다) — 성공을 기다리거나 결과를 알리지 않는다.
 * 기본은 [NoOpInAppReview]. Swift: `class StoreKitInAppReview: NSObject, InAppReview`
 */
interface InAppReview {
    fun requestReview()
}

object NoOpInAppReview : InAppReview {
    override fun requestReview() = Unit
}
