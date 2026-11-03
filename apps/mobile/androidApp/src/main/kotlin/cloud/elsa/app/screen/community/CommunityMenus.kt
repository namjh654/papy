package cloud.elsa.app.screen.community

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cloud.elsa.app.R
import cloud.elsa.app.core.common.time.RelativeTime
import cloud.elsa.app.designsystem.component.ElsaAction
import cloud.elsa.app.designsystem.format.label
import cloud.elsa.app.feature.community.ReportReason

/** 글·댓글 시간: 방금 전 · 5분 전 · … (KMP `RelativeTime`) */
@Composable
internal fun postTime(epochMillis: Long): String = RelativeTime.since(epochMillis).label()

/** 신고 사유 메뉴(App Store 1.2) — 고르면 [onPick] */
@Composable
internal fun reportReasonActions(onPick: (ReportReason) -> Unit): List<ElsaAction> = listOf(
    ElsaAction(stringResource(R.string.community_report_spam)) { onPick(ReportReason.Spam) },
    ElsaAction(stringResource(R.string.community_report_abuse)) { onPick(ReportReason.Abuse) },
    ElsaAction(stringResource(R.string.community_report_inappropriate)) { onPick(ReportReason.Inappropriate) },
    ElsaAction(stringResource(R.string.community_report_other)) { onPick(ReportReason.Other) },
)

/** 남의 글·댓글 메뉴: 신고 · 차단 */
@Composable
internal fun othersActions(onReport: () -> Unit, onBlock: () -> Unit): List<ElsaAction> = listOf(
    ElsaAction(stringResource(R.string.community_report), R.drawable.elsa_ic_report, onClick = onReport),
    ElsaAction(stringResource(R.string.community_block), R.drawable.elsa_ic_block, destructive = true, onClick = onBlock),
)
