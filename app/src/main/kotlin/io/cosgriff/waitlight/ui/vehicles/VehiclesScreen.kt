package io.cosgriff.waitlight.ui.vehicles

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cosgriff.waitlight.R
import io.cosgriff.waitlight.data.vans.DriveByRepository
import io.cosgriff.waitlight.data.vehicles.VehicleRepository
import io.cosgriff.waitlight.domain.VehicleRules
import io.cosgriff.waitlight.domain.VehicleWindow
import io.cosgriff.waitlight.domain.WindowState
import io.cosgriff.waitlight.domain.vehicleWindows
import io.cosgriff.waitlight.ui.TimeText
import io.cosgriff.waitlight.ui.components.WindowBar
import io.cosgriff.waitlight.ui.components.windowCountText
import io.cosgriff.waitlight.ui.components.windowStatusText
import io.cosgriff.waitlight.ui.theme.BannerColors
import io.cosgriff.waitlight.ui.theme.waitColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import javax.inject.Inject

@HiltViewModel
class VehiclesViewModel @Inject constructor(vehicles: VehicleRepository, driveBys: DriveByRepository, private val clock: Clock) : ViewModel() {
    val vehicles: StateFlow<List<VehicleWindow>> = combine(vehicles.vehicles, driveBys.driveBys) { list, log -> vehicleWindows(list, log, clock.instant()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), vehicleWindows(vehicles.vehicles.value, driveBys.driveBys.value, clock.instant()))
}

@Composable
fun VehiclesScreen(onBack: () -> Unit, onOpen: (Long) -> Unit, onAdd: () -> Unit) {
    val viewModel: VehiclesViewModel = hiltViewModel()
    val vehicles by viewModel.vehicles.collectAsStateWithLifecycle()
    VehiclesContent(vehicles, onBack, onOpen, onAdd)
}

/** The user's vehicles, each with its window, as drawn in `design/vehicles/source/Main.dc.html`. */
@Composable
fun VehiclesContent(vehicles: List<VehicleWindow>, onBack: () -> Unit, onOpen: (Long) -> Unit, onAdd: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "header") {
            Column(Modifier.statusBarsPadding().padding(start = 8.dp, end = 16.dp, top = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.vehicles_back))
                    }
                    Text(
                        stringResource(R.string.vehicles_title),
                        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp, letterSpacing = (-0.3).sp),
                        modifier = Modifier.semantics { heading() },
                    )
                }
                Text(
                    stringResource(R.string.vehicles_intro),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                    color = muted,
                    modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp),
                )
            }
        }
        items(vehicles, key = { it.vehicle.id }) { vehicle -> VehicleCard(vehicle, onClick = { onOpen(vehicle.vehicle.id) }) }
        item(key = "add") {
            OutlinedButton(
                onClick = onAdd,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.vehicles_add), modifier = Modifier.padding(start = 8.dp), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        item(key = "footer") {
            Text(
                stringResource(R.string.vehicles_footer),
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp),
                color = muted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        item(key = "end") { Spacer(Modifier.navigationBarsPadding()) }
    }
}

@Composable
private fun VehicleCard(vehicle: VehicleWindow, onClick: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val window = vehicle.window
    Surface(onClick = onClick, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                VehicleIcon()
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(vehicle.vehicle.description, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold))
                    Text(
                        stringResource(R.string.vehicles_renews, TimeText.monthYear(window.renews), TimeText.mediumDate(window.testDueBy)),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = muted,
                    )
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = muted)
            }
            when (val state = vehicle.state) {
                is WindowState.Open -> if (state.closingSoon && vehicle.count < VehicleRules.READINGS_NEEDED) {
                    val title = if (state.daysLeft == 0L) {
                        stringResource(R.string.vehicles_closing_today)
                    } else {
                        pluralStringResource(R.plurals.vehicles_closing_title, state.daysLeft.toInt(), TimeText.monthDay(window.closes), state.daysLeft.toInt())
                    }
                    val body = stringResource(if (vehicle.count == 0) R.string.vehicles_closing_none else R.string.vehicles_closing_one)
                    Note(title, body, MaterialTheme.waitColors.warning)
                } else {
                    Progress(vehicle, state.fraction)
                }
                is WindowState.Before -> Progress(vehicle, 0f)
                WindowState.Closed -> if (vehicle.count < VehicleRules.READINGS_NEEDED) {
                    Note(
                        stringResource(R.string.vehicles_closed_short, TimeText.monthDay(window.closes)),
                        stringResource(R.string.vehicles_closed_short_body, TimeText.mediumDate(window.testDueBy)),
                        MaterialTheme.waitColors.warning,
                    )
                } else {
                    Note(
                        null,
                        pluralStringResource(R.plurals.vehicles_closed_logged, vehicle.count, TimeText.monthDay(window.closes), vehicle.count, TimeText.mediumDate(window.testDueBy)),
                        MaterialTheme.waitColors.info,
                    )
                }
                is WindowState.Past -> Note(
                    null,
                    stringResource(R.string.vehicles_past, TimeText.monthYear(window.renews), TimeText.monthYear(state.next)),
                    MaterialTheme.waitColors.info,
                )
            }
        }
    }
}

/** The car in its tinted square. */
@Composable
internal fun VehicleIcon() {
    Box(
        Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.DirectionsCar, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
    }
}

/** The status and count, the bar, and the window's two dates. */
@Composable
private fun Progress(vehicle: VehicleWindow, fraction: Float) {
    val open = vehicle.state is WindowState.Open
    val statusColor = if (open) MaterialTheme.waitColors.short.icon else MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val bold = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(windowStatusText(vehicle), style = bold, color = statusColor, modifier = Modifier.weight(1f))
            Text(windowCountText(vehicle.count), style = bold)
        }
        WindowBar(vehicle.window, fraction, fill = MaterialTheme.waitColors.short.container, track = MaterialTheme.colorScheme.surfaceVariant)
        Row {
            val small = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
            Text(TimeText.mediumDate(vehicle.window.opens), style = small, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(TimeText.mediumDate(vehicle.window.closes), style = small, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A tinted note in the card: a warning icon when it's a warning, the [title] in bold before the [body]. */
@Composable
private fun Note(title: String?, body: String, colors: BannerColors) {
    val warning = colors == MaterialTheme.waitColors.warning
    Row(
        Modifier.fillMaxWidth().background(colors.container, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (warning) Icon(Icons.Rounded.Warning, contentDescription = null, tint = colors.content, modifier = Modifier.padding(top = 1.dp).size(18.dp))
        // Two whole sentences, the first in bold, run together as one paragraph.
        val text = buildAnnotatedString {
            if (title != null) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(title) }
                append(' ')
            }
            append(body)
        }
        Text(text, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp), color = colors.content)
    }
}
