package io.cosgriff.waitlight.ui.detail

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.cosgriff.waitlight.R
import io.cosgriff.waitlight.domain.Badge
import io.cosgriff.waitlight.domain.StationRow
import io.cosgriff.waitlight.domain.WaitBand
import io.cosgriff.waitlight.ui.TimeText
import io.cosgriff.waitlight.ui.components.BadgeSize
import io.cosgriff.waitlight.ui.components.WaitBadge
import io.cosgriff.waitlight.ui.theme.waitColors

@Composable
fun StationDetailScreen(stationId: Int, onBack: () -> Unit) {
    val viewModel: StationDetailViewModel = hiltViewModel()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val row = ui.rows[stationId] ?: return
    val context = LocalContext.current
    val chooserTitle = stringResource(R.string.detail_directions_chooser)
    StationDetailContent(
        row = row,
        badge = row.badge(ui.now, ui.thresholds, ui.dataUsable),
        onBack = onBack,
        onDirections = {
            // The system's own picker every time, so the user chooses the map app (CoMaps, Google Maps, …).
            val map = Intent(Intent.ACTION_VIEW, row.station.mapUri.toUri())
            context.open(Intent.createChooser(map, chooserTitle))
        },
        onCall = { context.open(Intent(Intent.ACTION_DIAL, "tel:${row.station.dialNumber}".toUri())) },
    )
}

private fun Context.open(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.i("Waitlight", "Nothing to open $intent with", e)
    }
}

/**
 * One station, as drawn in `design/png/Detail.png`: the station's map across the top (ADR-0006), and the rest on
 * a sheet over the map's bottom edge.
 */
@Composable
fun StationDetailContent(row: StationRow, badge: Badge, onBack: () -> Unit, onDirections: () -> Unit, onCall: () -> Unit) {
    val station = row.station
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val background = MaterialTheme.colorScheme.background
    Column(
        Modifier
            .fillMaxSize()
            .background(background)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding(),
    ) {
        // Laid out SHEET_OVERLAP shorter than the map, which draws on below it under the sheet: the overlap is in
        // the layout, so the scrolling column has no blank strip at its end (an offset sheet would leave one).
        Box(
            Modifier.fillMaxWidth().layout { measurable, constraints ->
                val full = MAP_HEIGHT.roundToPx()
                val placeable = measurable.measure(constraints.copy(minHeight = full, maxHeight = full))
                layout(placeable.width, full - SHEET_OVERLAP.roundToPx()) { placeable.place(0, 0) }
            },
        ) {
            StationMap(station, badge, bottomInset = SHEET_OVERLAP, modifier = Modifier.matchParentSize())
            FilledIconButton(
                onClick = onBack,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier.statusBarsPadding().padding(start = 12.dp, top = 14.dp).size(48.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.detail_back))
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                // Material's sheet corners: the theme's extraLarge, on top only.
                .background(background, MaterialTheme.shapes.extraLarge.copy(bottomStart = CornerSize(0), bottomEnd = CornerSize(0)))
                .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                WaitBadge(badge, BadgeSize.Detail, ringColor = background)
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        station.name,
                        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp),
                        modifier = Modifier.semantics { heading() },
                    )
                    val (status, statusColor) = status(badge)
                    Text(status, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = statusColor)
                    val drive = row.driveMinutes
                    val miles = row.distanceMiles
                    val total = row.totalMinutes
                    val small = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
                    if (miles != null) {
                        // A total only while the wait it adds is current; no drive time outside the routed area.
                        val text = when {
                            drive == null -> stringResource(R.string.detail_miles, miles)
                            total != null && badge is Badge.Live -> stringResource(R.string.detail_total, total, drive, miles)
                            else -> stringResource(R.string.detail_drive, drive, miles)
                        }
                        Text(text, style = small, color = muted)
                    }
                    row.updatedAt?.let { Text(stringResource(R.string.detail_updated, TimeText.time(it)), style = small, color = muted) }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onDirections, modifier = Modifier.weight(1f).height(52.dp)) {
                    Icon(Icons.Outlined.Navigation, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.detail_directions), modifier = Modifier.padding(start = 8.dp), fontSize = 15.sp)
                }
                OutlinedButton(
                    onClick = onCall,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    Icon(Icons.Outlined.Call, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(R.string.detail_call), modifier = Modifier.padding(start = 8.dp), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }

            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    InfoLine(stringResource(R.string.detail_address_label), stringResource(R.string.detail_address, station.street, station.city, station.zip))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    InfoLine(stringResource(R.string.detail_hours_label), stringResource(R.string.detail_hours))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    InfoLine(stringResource(R.string.detail_lanes_label), station.lanes.toString())
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    InfoLine(
                        stringResource(R.string.detail_rims_label),
                        stringResource(if (station.lowProfileRims) R.string.detail_rims_yes else R.string.detail_rims_no),
                    )
                }
            }

            val tip = MaterialTheme.waitColors.warning
            Surface(shape = MaterialTheme.shapes.medium, color = tip.container, contentColor = tip.content, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.detail_tip_title), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                    Text(stringResource(R.string.detail_tip), style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp))
                }
            }

            Text(
                stringResource(R.string.detail_footer),
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp),
                color = muted,
            )
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium, lineHeight = 20.sp), textAlign = TextAlign.End)
    }
}

/** The word under the name, in the badge's own colour. */
@Composable
private fun status(badge: Badge): Pair<String, Color> {
    val colors = MaterialTheme.waitColors
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    return when (badge) {
        is Badge.Live -> when (badge.band) {
            WaitBand.SHORT -> stringResource(R.string.detail_status_short) to colors.short.icon
            WaitBand.MODERATE -> stringResource(R.string.detail_status_moderate) to colors.moderate.icon
            WaitBand.LONG -> stringResource(R.string.detail_status_long) to colors.long.icon
        }
        Badge.Open -> stringResource(R.string.detail_status_open) to muted
        Badge.Closed -> stringResource(R.string.detail_status_closed) to muted
        Badge.NoReading -> stringResource(R.string.detail_status_no_reading) to muted
        is Badge.Stale -> stringResource(R.string.detail_status_stale) to muted
    }
}
