package cloud.elsa.app.review

import android.content.Context
import cloud.elsa.app.app.CurrentActivity
import cloud.elsa.app.core.data.review.InAppReview
import co.touchlab.kermit.Logger
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * Play In-App Review (KMP InAppReview 구현). Play 스토어로 설치한 앱에서만 실제로 뜨고, 횟수는 Play 가 제한한다.
 * 개발 빌드에서는 조용히 아무것도 안 뜰 수 있다 — docs/release.md "인앱 리뷰 확인".
 */
class PlayInAppReview(context: Context) : InAppReview {
    private val manager = ReviewManagerFactory.create(context.applicationContext)
    private val log = Logger.withTag("InAppReview")

    override fun requestReview() {
        manager.requestReviewFlow().addOnCompleteListener { request ->
            val activity = CurrentActivity.activity
            if (request.isSuccessful && activity != null) {
                manager.launchReviewFlow(activity, request.result)
            } else {
                log.i(request.exception) { "리뷰 요청 불가" }
            }
        }
    }
}
