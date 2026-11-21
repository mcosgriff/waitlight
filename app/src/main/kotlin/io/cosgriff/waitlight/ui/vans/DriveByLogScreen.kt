package io.cosgriff.waitlight.ui.vans

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import io.cosgriff.waitlight.domain.AirCareHours
import io.cosgriff.waitlight.domain.DriveBy
import io.cosgriff.waitlight.domain.DriveByLog
import io.cosgriff.waitlight.domain.DriveByRules
import io.cosgriff.waitlight.domain.driveByLog
import io.cosgriff.waitlight.ui.components.driveByWhen
import io.cosgriff.waitlight.ui.components.vanNameSpoken
import io.cosgriff.waitlight.ui.components.vanNameText
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

/** The drive-by log at [now], with each vehicle's description by its id. */
data class DriveByLogUi(val now: Instant, val log: DriveByLog, val vehicles: Map<Long, String> = emptyMap())

@HiltViewModel
class DriveByLogViewModel @Inject constructor(private val driveBys: DriveByRepository, vehicles: VehicleRepository, private val clock: Clock) : ViewModel() {
    val ui: StateFlow<DriveByLogUi> = combine(driveBys.driveBys, vehicles.vehicles) { log, list ->
        DriveByLogUi(clock.instant(), driveByLog(log, clock.instant()), list.associate { it.id to it.description })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DriveByLogUi(clock.instant(), driveByLog(driveBys.driveBys.value, clock.instant())))

    fun delete(id: Long) {
        viewModelScope.launch { driveBys.remove(id) }
    }
}

@Composable
fun DriveByLogScreen(onBack: () -> Unit) {
    val viewModel: DriveByLogViewModel = hiltViewModel()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    DriveByLogContent(ui, onBack, viewModel::delete)
}

/** The user's drive-bys, newest first, with how many count in the 14-month window. */
@Composable
fun DriveByLogContent(ui: DriveByLogUi, onBack: () -> Unit, onDelete: (Long) -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val windowStart = DriveByRules.windowStart(ui.now.atZone(AirCareHours.zone).toLocalDate()).atStartOfDay(AirCareHours.zone).toInstant()
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "header") {
            Column(Modifier.statusBarsPadding().padding(start = 8.dp, end = 20.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.driveby_back))
                    }
                    Text(
                        stringResource(R.string.driveby_list_title),
                        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp),
                        modifier = Modifier.semantics { heading() },
                    )
                }
                Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (ui.log.entries.isNotEmpty()) {
                        Text(
                            pluralStringResource(R.plurals.driveby_summary, ui.log.inWindow, ui.log.inWindow),
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                        )
                    }
                    Text(stringResource(R.string.driveby_list_body), style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp), color = muted)
                }
            }
        }
        if (ui.log.entries.isEmpty()) {
            item(key = "empty") {
                Text(
                    stringResource(R.string.driveby_list_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = muted,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
        items(ui.log.entries, key = { it.id }) { driveBy ->
            DriveByRow(driveBy, ui.now, ui.vehicles[driveBy.vehicleId], older = driveBy.at.isBefore(windowStart), onDelete = { onDelete(driveBy.id) })
        }
        item(key = "end") { Spacer(Modifier.navigationBarsPadding()) }
    }
}

@Composable
private fun DriveByRow(driveBy: DriveBy, now: Instant, vehicle: String?, older: Boolean, onDelete: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val whenText = driveByWhen(driveBy.at, now)
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
        Row(Modifier.heightIn(min = 64.dp).padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(vanNameText(driveBy.name), style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
                Text(whenText, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = muted)
                if (vehicle != null) Text(vehicle, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = muted)
                if (older) Text(stringResource(R.string.driveby_older), style = MaterialTheme.typography.bodySmall, color = muted)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.driveby_delete, "${vanNameSpoken(driveBy.name)}, $whenText"), tint = muted)
            }
        }
    }
}
