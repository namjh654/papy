package io.github.namjh654.papy.screen.community

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.namjh654.papy.R
import io.github.namjh654.papy.core.common.time.RelativeTime
import io.github.namjh654.papy.designsystem.component.PapyAction
import io.github.namjh654.papy.designsystem.format.label
import io.github.namjh654.papy.feature.community.ReportReason

/** 글·댓글 시간: 방금 전 · 5분 전 · … (KMP `RelativeTime`) */
@Composable
internal fun postTime(epochMillis: Long): String = RelativeTime.since(epochMillis).label()

/** 신고 사유 메뉴(App Store 1.2) — 고르면 [onPick] */
@Composable
internal fun reportReasonActions(onPick: (ReportReason) -> Unit): List<PapyAction> = listOf(
    PapyAction(stringResource(R.string.community_report_spam)) { onPick(ReportReason.Spam) },
    PapyAction(stringResource(R.string.community_report_abuse)) { onPick(ReportReason.Abuse) },
    PapyAction(stringResource(R.string.community_report_inappropriate)) { onPick(ReportReason.Inappropriate) },
    PapyAction(stringResource(R.string.community_report_other)) { onPick(ReportReason.Other) },
)

/** 남의 글·댓글 메뉴: 신고 · 차단 */
@Composable
internal fun othersActions(onReport: () -> Unit, onBlock: () -> Unit): List<PapyAction> = listOf(
    PapyAction(stringResource(R.string.community_report), R.drawable.papy_ic_report, onClick = onReport),
    PapyAction(stringResource(R.string.community_block), R.drawable.papy_ic_block, destructive = true, onClick = onBlock),
)
