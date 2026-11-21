package io.cosgriff.waitlight.ui.vans

import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.cosgriff.waitlight.R
import io.cosgriff.waitlight.domain.AirCareHours
import io.cosgriff.waitlight.domain.driveByAt
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * When the user drove past: a date (none after today, in Denver), then a time (none after [now]). Both start at
 * [initialDate] and now's time. [onPicked] gets the moment; it never gets one in the future.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveByPicker(now: Instant, initialDate: LocalDate, onPicked: (Instant) -> Unit, onDismiss: () -> Unit) {
    val local = now.atZone(AirCareHours.zone)
    val today = local.toLocalDate()
    // The date picker works in UTC midnights.
    val todayMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val notAfterToday = remember(todayMillis) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
            override fun isSelectableYear(year: Int) = year <= today.year
        }
    }
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = minOf(initialDate, today).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = notAfterToday,
    )
    var date by rememberSaveable { mutableStateOf<String?>(null) }
    val picked = date?.let(LocalDate::parse)
    if (picked == null) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = dateState.selectedDateMillis ?: return@TextButton
                        date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()
                    },
                    enabled = dateState.selectedDateMillis != null,
                ) { Text(stringResource(R.string.driveby_next)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.driveby_cancel)) } },
        ) {
            DatePicker(state = dateState, title = { Text(stringResource(R.string.driveby_pick_date), modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)) })
        }
    } else {
        val timeState = rememberTimePickerState(initialHour = local.hour, initialMinute = local.minute)
        val at = driveByAt(picked, LocalTime.of(timeState.hour, timeState.minute), now)
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.driveby_pick_time)) },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = { at?.let(onPicked) }, enabled = at != null) { Text(stringResource(R.string.driveby_save)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.driveby_cancel)) } },
        )
    }
}

