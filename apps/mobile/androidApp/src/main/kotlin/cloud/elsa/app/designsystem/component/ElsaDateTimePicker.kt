package cloud.elsa.app.designsystem.component

import android.text.format.DateFormat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import cloud.elsa.app.R
import cloud.elsa.app.designsystem.format.ElsaFormat
import cloud.elsa.app.designsystem.format.currentLocale
import cloud.elsa.app.designsystem.theme.ElsaTheme
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** 시:분 (0~23, 0~59). iOS 에서는 Date 의 시/분 컴포넌트에 대응한다. */
@Immutable
data class ElsaTime(val hour: Int, val minute: Int) {
    init {
        require(hour in 0..23 && minute in 0..59) { "잘못된 시간: $hour:$minute" }
    }

    /** 로케일 기준 표시 (ko: 오후 3:05, en: 3:05 PM) */
    fun format(locale: Locale, is24Hour: Boolean): String = ElsaFormat.time(hour, minute, locale, is24Hour)
}

/**
 * 날짜 값 도우미. DatePicker 는 "해당 날짜 00:00 UTC" 의 epoch millis 를 사용한다.
 */
object ElsaDate {
    private val utc: TimeZone = TimeZone.getTimeZone("UTC")

    fun utcMillis(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance(utc).apply {
            clear()
            set(year, month - 1, day)
        }.timeInMillis

    /** 기기 시간대 기준 오늘을 UTC 자정 millis 로. */
    fun todayUtcMillis(): Long {
        val local = Calendar.getInstance()
        return utcMillis(local.get(Calendar.YEAR), local.get(Calendar.MONTH) + 1, local.get(Calendar.DAY_OF_MONTH))
    }

    /** 로케일 기준 표시 (ko: 2026. 9. 29., en: Sep 29, 2026) */
    fun format(utcMillis: Long, locale: Locale): String = ElsaFormat.utcDate(utcMillis, locale)
}

/**
 * 날짜 선택 필드. 탭하면 Material3 DatePickerDialog 가 열린다.
 *
 * @param selectedDateMillis 선택된 날짜(UTC 자정 millis), 없으면 null
 * @param minDateMillis/maxDateMillis 선택 가능 범위(UTC 자정 millis, 포함)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElsaDatePickerField(
    selectedDateMillis: Long?,
    onDateSelected: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    helperText: String? = null,
    errorText: String? = null,
    enabled: Boolean = true,
    minDateMillis: Long? = null,
    maxDateMillis: Long? = null,
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    val locale = currentLocale

    ElsaSelectField(
        value = selectedDateMillis?.let { ElsaDate.format(it, locale) },
        onClick = { showDialog = true },
        modifier = modifier,
        label = label,
        placeholder = placeholder ?: stringResource(R.string.common_select_date),
        helperText = helperText,
        errorText = errorText,
        enabled = enabled,
        trailingIcon = R.drawable.elsa_ic_calendar,
    )

    if (showDialog) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = selectedDateMillis,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    (minDateMillis == null || utcTimeMillis >= minDateMillis) &&
                        (maxDateMillis == null || utcTimeMillis <= maxDateMillis)
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDateSelected(state.selectedDateMillis)
                        showDialog = false
                    },
                ) { Text(stringResource(R.string.common_confirm), style = ElsaTheme.typography.button) }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.common_cancel), style = ElsaTheme.typography.button, color = ElsaTheme.colors.onSurfaceVariant)
                }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

/** 시간 선택 필드. 탭하면 Material3 TimePicker 다이얼로그가 열린다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElsaTimePickerField(
    selectedTime: ElsaTime?,
    onTimeSelected: (ElsaTime) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    helperText: String? = null,
    errorText: String? = null,
    enabled: Boolean = true,
    is24Hour: Boolean = DateFormat.is24HourFormat(LocalContext.current),
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    val locale = currentLocale

    ElsaSelectField(
        value = selectedTime?.format(locale, is24Hour),
        onClick = { showDialog = true },
        modifier = modifier,
        label = label,
        placeholder = placeholder ?: stringResource(R.string.common_select_time),
        helperText = helperText,
        errorText = errorText,
        enabled = enabled,
        trailingIcon = R.drawable.elsa_ic_clock,
    )

    if (showDialog) {
        val now = Calendar.getInstance()
        val state = rememberTimePickerState(
            initialHour = selectedTime?.hour ?: now.get(Calendar.HOUR_OF_DAY),
            initialMinute = selectedTime?.minute ?: now.get(Calendar.MINUTE),
            is24Hour = is24Hour,
        )
        AlertDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onTimeSelected(ElsaTime(state.hour, state.minute))
                        showDialog = false
                    },
                ) { Text(stringResource(R.string.common_confirm), style = ElsaTheme.typography.button) }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.common_cancel), style = ElsaTheme.typography.button, color = ElsaTheme.colors.onSurfaceVariant)
                }
            },
            text = { TimePicker(state = state) },
            containerColor = ElsaTheme.colors.surface,
        )
    }
}
