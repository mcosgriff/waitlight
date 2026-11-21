package io.cosgriff.waitlight.ui.vans

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.cosgriff.waitlight.R
import io.cosgriff.waitlight.domain.AirCareHours
import io.cosgriff.waitlight.domain.VehicleRules
import io.cosgriff.waitlight.domain.VehicleWindow
import io.cosgriff.waitlight.domain.WindowState
import io.cosgriff.waitlight.domain.driveByAt
import io.cosgriff.waitlight.domain.firstChoice
import io.cosgriff.waitlight.ui.TimeText
import io.cosgriff.waitlight.ui.theme.waitColors
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * "Log a drive-by", as drawn in `design/vehicles/source/LogDriveBy.dc.html`: which vehicle drove past [site] (asked
 * only with two or more [vehicles]; with one it's that one), then a date (none after today, in Denver) and a time
 * (none after [now]), starting at [initialDate] and now's time. [onSave] never gets a moment in the future.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveBySheet(
    site: String,
    now: Instant,
    initialDate: LocalDate,
    vehicles: List<VehicleWindow>,
    onSave: (at: Instant, vehicleId: Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    val local = now.atZone(AirCareHours.zone)
    val today = local.toLocalDate()
    var date by rememberSaveable { mutableStateOf(minOf(initialDate, today).toString()) }
    var hour by rememberSaveable { mutableIntStateOf(local.hour) }
    var minute by rememberSaveable { mutableIntStateOf(local.minute) }
    var vehicleId by rememberSaveable { mutableStateOf(firstChoice(vehicles)?.vehicle?.id) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    val picked = LocalDate.parse(date)
    val at = driveByAt(picked, LocalTime.of(hour, minute), now)

    if (pickingDate) DateDialog(picked, today, onPicked = { date = it.toString(); pickingDate = false }, onDismiss = { pickingDate = false })
    if (pickingTime) {
        TimeDialog(hour, minute, onPicked = { h, m -> hour = h; minute = m; pickingTime = false }, onDismiss = { pickingTime = false })
    }

    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 22.dp, end = 22.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.driveby_log), style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold), modifier = Modifier.semantics { heading() })
                Text(site, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), color = muted)
            }
            if (vehicles.size >= 2) {
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.driveby_which), style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(bottom = 4.dp))
                    vehicles.forEach { vehicle ->
                        VehicleChoice(vehicle, selected = vehicle.vehicle.id == vehicleId, onSelect = { vehicleId = vehicle.vehicle.id })
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(stringResource(R.string.driveby_date), TimeText.shortDate(picked), onClick = { pickingDate = true }, modifier = Modifier.weight(3f))
                Field(stringResource(R.string.driveby_time), TimeText.time(LocalTime.of(hour, minute)), onClick = { pickingTime = true }, modifier = Modifier.weight(2f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.weight(1f).height(48.dp)) {
                    Text(stringResource(R.string.driveby_cancel), color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = { at?.let { onSave(it, vehicleId ?: vehicles.singleOrNull()?.vehicle?.id) } },
                    enabled = at != null,
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text(stringResource(R.string.driveby_save), fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

/** A vehicle to pick, with where its window stands and what this drive-by would make the count. */
@Composable
private fun VehicleChoice(vehicle: VehicleWindow, selected: Boolean, onSelect: () -> Unit) {
    val colors = MaterialTheme.waitColors
    val would = vehicle.count + 1
    val needed = VehicleRules.READINGS_NEEDED
    val (line, color) = when (val state = vehicle.state) {
        is WindowState.Open -> if (state.closingSoon) {
            val closes = TimeText.monthDay(vehicle.window.closes)
            (if (would <= needed) stringResource(R.string.driveby_choice_closing, closes, would, needed) else stringResource(R.string.driveby_choice_closing_many, closes)) to colors.moderate.icon
        } else {
            (if (would <= needed) stringResource(R.string.driveby_choice_open, would, needed) else stringResource(R.string.driveby_choice_open_many)) to colors.short.icon
        }
        is WindowState.Before -> stringResource(R.string.driveby_choice_before, TimeText.mediumDate(vehicle.window.opens)) to MaterialTheme.colorScheme.onSurfaceVariant
        WindowState.Closed -> stringResource(R.string.driveby_choice_closed, TimeText.monthYear(vehicle.window.renews)) to MaterialTheme.colorScheme.onSurfaceVariant
        is WindowState.Past -> stringResource(R.string.driveby_choice_past) to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
    ) {
        Row(Modifier.heightIn(min = 56.dp).padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(horizontal = 8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(vehicle.vehicle.description, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
                Text(line, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold), color = color)
            }
        }
    }
}

/** A labelled value that opens its picker. */
@Composable
private fun Field(label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(
            onClick = onClick,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** The day: none after [today]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(initial: LocalDate, today: LocalDate, onPicked: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    // The date picker works in UTC midnights.
    val todayMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val notAfterToday = remember(todayMillis) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
            override fun isSelectableYear(year: Int) = year <= today.year
        }
    }
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), selectableDates = notAfterToday)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onPicked(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } },
                enabled = state.selectedDateMillis != null,
            ) { Text(stringResource(R.string.driveby_done)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.driveby_cancel)) } },
    ) {
        DatePicker(state = state, title = { Text(stringResource(R.string.driveby_pick_date), modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)) })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(hour: Int, minute: Int, onPicked: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = hour, initialMinute = minute)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.driveby_pick_time)) },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onPicked(state.hour, state.minute) }) { Text(stringResource(R.string.driveby_done)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.driveby_cancel)) } },
    )
}
