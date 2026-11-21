package io.cosgriff.waitlight.ui.vehicles

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cosgriff.waitlight.R
import io.cosgriff.waitlight.data.vans.DriveByRepository
import io.cosgriff.waitlight.data.vehicles.VehicleRepository
import io.cosgriff.waitlight.domain.ReminderKind
import io.cosgriff.waitlight.domain.VehicleRules
import io.cosgriff.waitlight.domain.VehicleWindow
import io.cosgriff.waitlight.domain.WindowState
import io.cosgriff.waitlight.domain.vehicleWindows
import io.cosgriff.waitlight.ui.TimeText
import io.cosgriff.waitlight.ui.components.WindowBar
import io.cosgriff.waitlight.ui.components.driveByWhen
import io.cosgriff.waitlight.ui.components.rememberNotificationAsk
import io.cosgriff.waitlight.ui.components.vanNameText
import io.cosgriff.waitlight.ui.components.windowStatusText
import io.cosgriff.waitlight.ui.theme.waitColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

/** One vehicle at [now]; [vehicle] is null once the vehicles are read and it isn't among them (deleted). */
data class VehicleDetailUi(val now: Instant, val vehicle: VehicleWindow?)

@HiltViewModel
class VehicleDetailViewModel @Inject constructor(
    private val vehicles: VehicleRepository,
    driveBys: DriveByRepository,
    private val clock: Clock,
) : ViewModel() {
    private val id = MutableStateFlow<Long?>(null)

    val ui: StateFlow<VehicleDetailUi?> = combine(id.filterNotNull(), vehicles.loadedVehicles, driveBys.driveBys) { id, list, log ->
        val now = clock.instant()
        VehicleDetailUi(now, vehicleWindows(list, log, now).firstOrNull { it.vehicle.id == id })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun show(id: Long) {
        this.id.value = id
    }

    fun setReminder(kind: ReminderKind, on: Boolean) {
        val id = id.value ?: return
        viewModelScope.launch { vehicles.setReminder(id, kind, on, clock.instant()) }
    }
}

@Composable
fun VehicleDetailScreen(id: Long, onBack: () -> Unit, onEdit: () -> Unit, onFindVan: () -> Unit) {
    val viewModel: VehicleDetailViewModel = hiltViewModel()
    LaunchedEffect(id) { viewModel.show(id) }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val current = ui ?: return
    val vehicle = current.vehicle
    if (vehicle == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    LifecycleResumeEffect(Unit) {
        enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        onPauseOrDispose {}
    }
    var denied by rememberSaveable { mutableStateOf(false) }
    var asking by rememberSaveable { mutableStateOf<ReminderKind?>(null) }
    val ask = rememberNotificationAsk { granted ->
        enabled = granted
        denied = !granted
        if (granted) asking?.let { viewModel.setReminder(it, true) }
        asking = null
    }
    VehicleDetailContent(
        vehicle = vehicle,
        now = current.now,
        notificationsOff = !enabled && (denied || vehicle.vehicle.reminders.isNotEmpty()),
        onBack = onBack,
        onEdit = onEdit,
        onFindVan = onFindVan,
        onReminder = { kind, on ->
            if (on && !enabled) {
                asking = kind
                ask()
            } else {
                viewModel.setReminder(kind, on)
            }
        },
        onOpenSettings = {
            try {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            } catch (e: ActivityNotFoundException) {
                Log.i("Waitlight", "No notification settings to open", e)
            }
        },
    )
}

/** A vehicle's window, drive-bys, dates and reminders, as drawn in `design/vehicles/source/VehicleDetail.dc.html`. */
@Composable
fun VehicleDetailContent(
    vehicle: VehicleWindow,
    now: Instant,
    notificationsOff: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onFindVan: () -> Unit,
    onReminder: (ReminderKind, Boolean) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val window = vehicle.window
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(Modifier.padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.vehicle_back))
            }
            Text(
                vehicle.vehicle.description,
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.vehicle_edit))
            }
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            WindowCard(vehicle)
            DriveBysCard(vehicle, now)
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Fact(stringResource(R.string.vehicle_renews), TimeText.monthYear(window.renews), divider = false)
                    Fact(stringResource(R.string.vehicle_due), stringResource(R.string.vehicle_due_value, TimeText.mediumDate(window.testDueBy)))
                    Fact(stringResource(R.string.vehicle_fallback), stringResource(R.string.vehicle_fallback_value, TimeText.month(window.renews)))
                }
            }
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text(
                        stringResource(R.string.vehicle_reminders),
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                    val on = vehicle.vehicle.reminders
                    ReminderSwitch(stringResource(R.string.reminder_switch_opens), ReminderKind.WINDOW_OPENS in on) { onReminder(ReminderKind.WINDOW_OPENS, it) }
                    ReminderSwitch(stringResource(R.string.reminder_switch_halfway), ReminderKind.HALFWAY in on) { onReminder(ReminderKind.HALFWAY, it) }
                    ReminderSwitch(stringResource(R.string.reminder_switch_closing), ReminderKind.CLOSING_SOON in on) { onReminder(ReminderKind.CLOSING_SOON, it) }
                    ReminderSwitch(stringResource(R.string.reminder_switch_due), ReminderKind.TEST_DUE in on) { onReminder(ReminderKind.TEST_DUE, it) }
                    if (notificationsOff) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            stringResource(R.string.vehicle_notifications_off),
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                            color = muted,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                        TextButton(onClick = onOpenSettings, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.vehicle_notifications_settings)) }
                    }
                }
            }
            Button(onClick = onFindVan, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Icon(Icons.Rounded.LocalShipping, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.vehicle_find_van), modifier = Modifier.padding(start = 8.dp), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** The hero: where today falls in the window, with its two dates. */
@Composable
private fun WindowCard(vehicle: VehicleWindow) {
    val colors = MaterialTheme.waitColors
    val window = vehicle.window
    val state = vehicle.state
    val title = when (state) {
        is WindowState.Open -> windowStatusText(vehicle)
        is WindowState.Before -> pluralStringResource(R.plurals.vehicle_hero_before, state.daysUntil.toInt(), state.daysUntil.toInt())
        WindowState.Closed -> stringResource(R.string.vehicle_hero_closed)
        is WindowState.Past -> stringResource(R.string.vehicle_hero_past)
    }
    val fraction = when (state) {
        is WindowState.Open -> state.fraction
        is WindowState.Before -> 0f
        else -> 1f
    }
    val started = state !is WindowState.Before
    val ended = state is WindowState.Closed || state is WindowState.Past
    Surface(shape = MaterialTheme.shapes.large, color = colors.hero, contentColor = Color.White, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(R.string.vehicle_window_label).uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
                color = colors.heroLabel,
            )
            Text(title, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold), color = Color.White)
            WindowBar(
                window,
                fraction,
                fill = colors.heroAccent,
                track = Color.White.copy(alpha = 0.22f),
                height = 12.dp,
                marker = if (state is WindowState.Open) Color.White else null,
                modifier = Modifier.padding(top = 2.dp),
            )
            Row {
                val small = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
                val opens = TimeText.mediumDate(window.opens)
                val closes = TimeText.mediumDate(window.closes)
                Text(stringResource(if (started) R.string.vehicle_opened else R.string.vehicle_opens, opens), style = small, color = colors.heroDetail, modifier = Modifier.weight(1f))
                Text(stringResource(if (ended) R.string.vehicle_closed_on else R.string.vehicle_closes, closes), style = small, color = colors.heroDetail)
            }
        }
    }
}

/** "Drive-bys in the window · 1 of 2", a segment per reading needed, and each drive-by. */
@Composable
private fun DriveBysCard(vehicle: VehicleWindow, now: Instant) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.vehicle_drivebys_title), style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                val count = if (vehicle.count < VehicleRules.READINGS_NEEDED) {
                    stringResource(R.string.vehicle_drivebys_count, vehicle.count, VehicleRules.READINGS_NEEDED)
                } else {
                    vehicle.count.toString()
                }
                Text(count, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
            }
            Row(Modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(VehicleRules.READINGS_NEEDED) { i ->
                    val color = if (i < vehicle.count) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    Box(Modifier.weight(1f).height(8.dp).background(color, RoundedCornerShape(4.dp)))
                }
            }
            if (vehicle.driveBys.isEmpty()) {
                Text(stringResource(R.string.vehicle_drivebys_none), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), color = muted)
            }
            vehicle.driveBys.forEach { driveBy ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(vanNameText(driveBy.name), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), modifier = Modifier.weight(1f))
                    Text(driveByWhen(driveBy.at, now), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), color = muted)
                }
            }
            Text(stringResource(R.string.vehicle_drivebys_note), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp), color = muted)
        }
    }
}

@Composable
private fun Fact(label: String, value: String, divider: Boolean = true) {
    if (divider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
    }
}

/** A reminder's switch; the whole row toggles it. */
@Composable
private fun ReminderSwitch(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = on, role = Role.Switch, onValueChange = onChange).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), modifier = Modifier.weight(1f))
        Switch(checked = on, onCheckedChange = null)
    }
}
