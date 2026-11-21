package io.cosgriff.waitlight.ui.vehicles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cosgriff.waitlight.R
import io.cosgriff.waitlight.data.vehicles.VehicleRepository
import io.cosgriff.waitlight.domain.AirCareHours
import io.cosgriff.waitlight.domain.RapidScreenWindow
import io.cosgriff.waitlight.domain.Vehicle
import io.cosgriff.waitlight.domain.WindowState
import io.cosgriff.waitlight.ui.TimeText
import io.cosgriff.waitlight.ui.components.rememberNotificationAsk
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/** What's typed and picked so far. [month] is 1 to 12, null until chosen. */
data class VehicleForm(val description: String = "", val month: Int? = null, val year: Int, val remind: Boolean = true) {
    val renews: YearMonth? get() = month?.let { YearMonth.of(year, it) }
    val complete: Boolean get() = description.isNotBlank() && month != null
}

@HiltViewModel
class EditVehicleViewModel @Inject constructor(private val vehicles: VehicleRepository, private val clock: Clock) : ViewModel() {
    val today: LocalDate get() = clock.instant().atZone(AirCareHours.zone).toLocalDate()

    /** The vehicles once read: the one being edited is looked up here. */
    val all: StateFlow<List<Vehicle>?> = vehicles.loadedVehicles.map<List<Vehicle>, List<Vehicle>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun add(form: VehicleForm, remind: Boolean, onSaved: (Long) -> Unit) {
        val renews = form.renews ?: return
        viewModelScope.launch { onSaved(vehicles.add(form.description, renews, remind, clock.instant())) }
    }

    fun update(id: Long, form: VehicleForm, onSaved: () -> Unit) {
        val renews = form.renews ?: return
        viewModelScope.launch {
            vehicles.update(id, form.description, renews, clock.instant())
            onSaved()
        }
    }

    fun delete(id: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            vehicles.remove(id)
            onDone()
        }
    }
}

/**
 * Adds a vehicle ([id] null) or edits one. Saving a new one with "Remind me" on asks for permission to post
 * notifications first; refused, the vehicle is saved with its reminders off.
 */
@Composable
fun EditVehicleScreen(id: Long?, onClose: () -> Unit, onAdded: (Long) -> Unit, onDeleted: () -> Unit) {
    val viewModel: EditVehicleViewModel = hiltViewModel()
    val today = viewModel.today
    val all by viewModel.all.collectAsStateWithLifecycle()
    var form by rememberSaveable(stateSaver = FormSaver) { mutableStateOf(VehicleForm(year = today.year + 1)) }
    var filled by rememberSaveable { mutableStateOf(id == null) }
    val editing = id?.let { all?.firstOrNull { v -> v.id == it } }
    LaunchedEffect(editing) {
        if (!filled && editing != null) {
            form = VehicleForm(editing.description, editing.renews.monthValue, editing.renews.year)
            filled = true
        }
    }
    // Edited away under us (deleted from another screen): nothing to edit.
    if (id != null && all != null && editing == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    if (!filled) return
    val ask = rememberNotificationAsk { granted -> viewModel.add(form, granted, onAdded) }
    EditVehicleContent(
        form = form,
        today = today,
        editing = id != null,
        onChange = { form = it },
        onClose = onClose,
        onSave = {
            when {
                id != null -> viewModel.update(id, form, onClose)
                form.remind -> ask()
                else -> viewModel.add(form, false, onAdded)
            }
        },
        onDelete = { id?.let { viewModel.delete(it, onDeleted) } },
    )
}

/** As drawn in `design/vehicles/source/AddVehicle.dc.html`: what the vehicle is, when it renews, and its window. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditVehicleContent(
    form: VehicleForm,
    today: LocalDate,
    editing: Boolean,
    onChange: (VehicleForm) -> Unit,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit = {},
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.vehicle_delete_title, form.description.trim())) },
            text = { Text(stringResource(R.string.vehicle_delete_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(stringResource(R.string.vehicle_delete_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.vehicle_delete_cancel)) } },
        )
    }
    val small = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().imePadding()) {
        Row(Modifier.padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.vehicle_edit_close))
            }
            Text(
                stringResource(if (editing) R.string.vehicle_edit_edit_title else R.string.vehicle_edit_add_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.semantics { heading() },
            )
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = form.description,
                    onValueChange = { onChange(form.copy(description = it.take(MAX_DESCRIPTION))) },
                    label = { Text(stringResource(R.string.vehicle_edit_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.vehicle_edit_hint), style = small, color = muted)
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.vehicle_edit_renews), style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val months = (1..12).map { YearMonth.of(today.year, it) }
                    Picker(
                        label = stringResource(R.string.vehicle_edit_month),
                        value = form.month?.let { TimeText.month(YearMonth.of(today.year, it)) } ?: stringResource(R.string.vehicle_edit_month_none),
                        options = months.map { TimeText.month(it) },
                        onPick = { onChange(form.copy(month = it + 1)) },
                        modifier = Modifier.weight(3f),
                    )
                    val years = (minOf(form.year, today.year - 1)..maxOf(form.year, today.year + YEARS_AHEAD)).toList()
                    Picker(
                        label = stringResource(R.string.vehicle_edit_year),
                        value = form.year.toString(),
                        options = years.map { it.toString() },
                        onPick = { onChange(form.copy(year = years[it])) },
                        modifier = Modifier.weight(2f),
                    )
                }
                Text(stringResource(R.string.vehicle_edit_renews_hint), style = small, color = muted)
            }
            form.renews?.let { Preview(RapidScreenWindow.of(it), today) }
            if (!editing) {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier
                            .toggleable(value = form.remind, role = Role.Switch, onValueChange = { onChange(form.copy(remind = it)) })
                            .heightIn(min = 52.dp)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(stringResource(R.string.vehicle_edit_remind), style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
                            Text(stringResource(R.string.vehicle_edit_remind_detail), style = small, color = muted)
                        }
                        Switch(checked = form.remind, onCheckedChange = null)
                    }
                }
                Text(stringResource(R.string.vehicle_edit_note), style = small, color = muted)
            } else {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.vehicle_edit_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Button(
            onClick = onSave,
            enabled = form.complete,
            modifier = Modifier.navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp).fillMaxWidth().height(52.dp),
        ) {
            Text(stringResource(R.string.vehicle_edit_save), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** The window the renewal gives, worked out as the user picks. */
@Composable
private fun Preview(window: RapidScreenWindow, today: LocalDate) {
    val primary = MaterialTheme.colorScheme.primary
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.vehicle_edit_preview_title), style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = primary)
            Text(
                stringResource(R.string.vehicle_edit_preview_dates, TimeText.mediumDate(window.opens), TimeText.mediumDate(window.closes)),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            val body = when (window.stateOn(today)) {
                is WindowState.Before, is WindowState.Open -> stringResource(
                    R.string.vehicle_edit_preview_body,
                    TimeText.month(window.renews),
                    TimeText.monthYear(window.renews),
                    TimeText.mediumDate(window.testDueBy),
                )
                WindowState.Closed -> stringResource(R.string.vehicle_edit_preview_closed, TimeText.mediumDate(window.closes), TimeText.mediumDate(window.testDueBy))
                is WindowState.Past -> stringResource(R.string.vehicle_edit_preview_past)
            }
            Text(body, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 19.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A read-only dropdown: the month or the year. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Picker(label: String, value: String, options: List<String>, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }, modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { i, option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { open = false; onPick(i) })
            }
        }
    }
}

private const val MAX_DESCRIPTION = 60

/** Renewal years offered after this one. */
private const val YEARS_AHEAD = 3

private val FormSaver = listSaver<VehicleForm, Any?>(
    save = { listOf(it.description, it.month, it.year, it.remind) },
    restore = { VehicleForm(it[0] as String, it[1] as Int?, it[2] as Int, it[3] as Boolean) },
)
