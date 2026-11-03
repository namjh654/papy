package io.github.namjh654.papy.designsystem.format

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.namjh654.papy.R
import io.github.namjh654.papy.core.common.time.RelativeTime

/** [RelativeTime] → 문구. 예) 방금 전 · 5분 전 · 3시간 전 · 2일 전 · 2026. 9. 20. (iOS `RelativeTime.label`) */
@Composable
fun RelativeTime.label(): String = when (this) {
    RelativeTime.JustNow -> stringResource(R.string.time_just_now)
    is RelativeTime.Minutes -> stringResource(R.string.time_minutes_ago, count)
    is RelativeTime.Hours -> stringResource(R.string.time_hours_ago, count)
    is RelativeTime.Days -> stringResource(R.string.time_days_ago, count)
    is RelativeTime.Date -> PapyFormat.date(epochMillis, currentLocale)
}
