package io.cosgriff.waitlight.ui.vans

import android.content.ActivityNotFoundException
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.AddTask
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.cosgriff.waitlight.R
import io.cosgriff.waitlight.domain.Heading
import io.cosgriff.waitlight.domain.VanName
import io.cosgriff.waitlight.domain.VanDetailHours
import io.cosgriff.waitlight.domain.VanPin
import io.cosgriff.waitlight.domain.VanWeekLine
import io.cosgriff.waitlight.domain.vanRowStatus
import io.cosgriff.waitlight.ui.TimeText
import io.cosgriff.waitlight.ui.components.DriveTimePill
import io.cosgriff.waitlight.ui.components.VanBadge
import io.cosgriff.waitlight.ui.components.driveByWhen
import io.cosgriff.waitlight.ui.components.rowStatusText
import io.cosgriff.waitlight.ui.components.vanHoursText
import io.cosgriff.waitlight.ui.components.vanNameSpoken
import io.cosgriff.waitlight.ui.components.vanNameText
import io.cosgriff.waitlight.ui.components.vanStatusColor
import io.cosgriff.waitlight.ui.theme.waitColors
import java.time.Instant
import java.time.LocalDate

@Composable
fun VanDetailScreen(key: String, date: LocalDate, onBack: () -> Unit) {
    val viewModel: VanDetailViewModel = hiltViewModel()
    LaunchedEffect(key, date) { viewModel.show(key, date) }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val current = ui ?: return
    val row = current.detail?.row ?: return
    val context = LocalContext.current
    val chooserTitle = stringResource(R.string.van_detail_directions_chooser)
    val label = vanNameSpoken(row.site.name)
    VanDetailContent(
        ui = current,
        onBack = onBack,
        onLogDriveBy = viewModel::logDriveBy,
        onDirections = {
            // The system's own picker every time, so the user chooses the map app, as for a station.
            val map = Intent(Intent.ACTION_VIEW, row.site.mapUri(label).toUri())
            try {
                context.startActivity(Intent.createChooser(map, chooserTitle))
            } catch (e: ActivityNotFoundException) {
                Log.i("Waitlight", "Nothing to open $map with", e)
            }
        },
    )
}

/** A van site, as drawn in `design/vans/source/VanDetail.dc.html`: a drawn diagram (no map tiles), then a sheet. */
@Composable
fun VanDetailContent(ui: VanDetailUi, onBack: () -> Unit, onDirections: () -> Unit, onLogDriveBy: (Instant, Long?) -> Unit = { _, _ -> }) {
    val detail = ui.detail ?: return
    val row = detail.row
    var picking by rememberSaveable { mutableStateOf(false) }
    if (picking) {
        DriveBySheet(
            site = vanNameText(row.site.name),
            now = ui.now,
            initialDate = row.date,
            vehicles = ui.vehicles,
            onSave = { at, vehicle -> picking = false; onLogDriveBy(at, vehicle) },
            onDismiss = { picking = false },
        )
    }
    val site = row.site
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val background = MaterialTheme.colorScheme.background
    val status = rowStatusText(vanRowStatus(row, detail.state, detail.isToday))
    Column(
        Modifier
            .fillMaxSize()
            .background(background)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding(),
    ) {
        Box(Modifier.fillMaxWidth().height(DIAGRAM_HEIGHT)) {
            SiteDiagram(site.name, detail.pin, Modifier.matchParentSize())
            FilledIconButton(
                onClick = onBack,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier.statusBarsPadding().padding(start = 12.dp, top = 14.dp).size(48.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.van_detail_back))
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .offset(y = (-16).dp)
                .background(background, MaterialTheme.shapes.large.copy(bottomStart = CornerSize(0), bottomEnd = CornerSize(0)))
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 0.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                VanBadge(row.status, status, 64.dp, ringColor = background)
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        vanNameText(site.name),
                        style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp),
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(status, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = vanStatusColor(row.status))
                    val hours = when (val shown = detail.hours) {
                        is VanDetailHours.Until -> stringResource(R.string.van_detail_until, TimeText.time(shown.closes))
                        is VanDetailHours.Range -> vanHoursText(shown.opens, shown.closes)
                    }
                    val small = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
                    Text(stringResource(R.string.van_detail_place, row.place, hours), style = small, color = muted)
                    detail.checkedAt?.let { Text(stringResource(R.string.van_detail_checked, TimeText.dateTime(it)), style = small, color = muted) }
                    row.drive?.let { DriveTimePill(it, Modifier.padding(top = 4.dp)) }
                }
            }

            Button(onClick = onDirections, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Icon(Icons.Outlined.Navigation, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.van_detail_directions), modifier = Modifier.padding(start = 8.dp), fontSize = 16.sp)
            }

            OutlinedButton(
                onClick = { picking = true },
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Icon(Icons.Outlined.AddTask, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
                Text(stringResource(R.string.driveby_log), modifier = Modifier.padding(start = 8.dp), fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
            }

            if (detail.driveBys.isNotEmpty()) {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.driveby_logged_here), style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
                        detail.driveBys.forEach { driveBy ->
                            Text(driveByWhen(driveBy.at, ui.now), style = MaterialTheme.typography.bodyMedium, color = muted)
                        }
                    }
                }
            }

            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.van_detail_get_read_title), style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
                    val how = when (val name = site.name) {
                        is VanName.Ramp -> stringResource(R.string.van_detail_get_read_ramp, name.from, name.onto)
                        is VanName.Road -> stringResource(R.string.van_detail_get_read_road, name.road, name.past)
                        is VanName.Plain -> stringResource(R.string.van_detail_get_read_plain)
                    }
                    Text(how, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp), color = muted)
                    if (site.twoUnits) Text(stringResource(R.string.van_detail_two_units), style = MaterialTheme.typography.bodyMedium, color = muted)
                }
            }

            WeekCard(detail.week)

            Text(stringResource(R.string.van_detail_footer), style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp), color = muted)
        }
    }
}

@Composable
private fun WeekCard(lines: List<VanWeekLine>) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            Text(
                stringResource(R.string.van_detail_week_title),
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
            )
            lines.forEach { line ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                when (line) {
                    is VanWeekLine.Visit -> {
                        val day = if (line.isToday) stringResource(R.string.van_detail_today, TimeText.shortDate(line.date)) else TimeText.shortDate(line.date)
                        WeekLine(day, vanHoursText(line.opens, line.closes), bold = line.isToday, muted = line.isPast)
                    }
                    VanWeekLine.NextWeekNotScheduled ->
                        WeekLine(stringResource(R.string.van_detail_next_week), stringResource(R.string.van_detail_not_scheduled), bold = false, muted = true)
                }
            }
            Box(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun WeekLine(day: String, hours: String, bold: Boolean, muted: Boolean) {
    val color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    val weight = if (bold) FontWeight.SemiBold else FontWeight.Normal
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(day, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = weight), color = color)
        Text(hours, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = weight), color = color)
    }
}

/**
 * The site drawn, not mapped: weekly sites have no bundled map (ADR-0006 is for the fixed stations). It is drawn
 * eastbound and turned to the site's direction of travel ([VanName.heading]): a ramp is the highway with the cross
 * street and the on-ramp joining from the right of the traffic, as in the US, the unit on it; a road site is the
 * road with the cross street and the unit just past it. An arrow shows which way the traffic goes. The content
 * description says the same in words, and the status is in the text below, so colour is not the only signal.
 */
@Composable
private fun SiteDiagram(name: VanName, pin: VanPin, modifier: Modifier = Modifier) {
    val road = MaterialTheme.colorScheme.background
    val ground = MaterialTheme.colorScheme.surfaceContainerHighest
    val lane = MaterialTheme.colorScheme.outline
    val accent = MaterialTheme.colorScheme.primary
    val pinColor = when (pin) {
        VanPin.READING -> MaterialTheme.waitColors.short.container
        VanPin.SET_UP -> MaterialTheme.waitColors.open.container
        VanPin.OTHER -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val labelStyle = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    val unitStyle = labelStyle.copy(color = accent, fontSize = 12.sp)
    val measurer = rememberTextMeasurer()
    val description = when (name) {
        is VanName.Ramp -> stringResource(R.string.van_detail_diagram_ramp, name.from, name.onto)
        is VanName.Road -> stringResource(R.string.van_detail_diagram_road, name.road, name.past)
        is VanName.Plain -> stringResource(R.string.van_detail_diagram_plain, name.text)
    }
    val (mainLabel, crossLabel) = when (name) {
        is VanName.Ramp -> name.onto to name.from
        is VanName.Road -> name.road to name.past
        is VanName.Plain -> name.text to null
    }
    val unitLabel = stringResource(if (name is VanName.Ramp) R.string.van_detail_unit_ramp else R.string.van_detail_unit_road)
    val heading = name.heading ?: Heading.EAST
    Canvas(modifier.background(ground).clearAndSetSemantics { contentDescription = description; role = Role.Image }) {
        // Drawn in the traffic's own frame: `along` runs the way the traffic goes, `across` to its right, each -1..1
        // over the canvas, then placed by heading. The highway sits a little to the traffic's left, so the ramp
        // and the unit have room on its right.
        val frame = TrafficFrame(heading, center, size.width / 2, size.height / 2)
        val band = 40.dp.toPx()
        val roadB = -0.25f
        val crossA = -0.45f
        val laneB = roadB + frame.acrossFraction(band * 0.25f)
        drawLine(road, frame.at(-2f, roadB), frame.at(2f, roadB), strokeWidth = band)
        drawLine(lane, frame.at(-2f, roadB), frame.at(2f, roadB), strokeWidth = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14.dp.toPx(), 12.dp.toPx())))
        if (crossLabel != null) drawLine(road, frame.at(crossA, -2f), frame.at(crossA, 2f), strokeWidth = 34.dp.toPx())
        // Which way the traffic goes.
        val tip = frame.at(0.72f, laneB)
        val tail = frame.at(0.42f, laneB)
        drawLine(accent, tail, tip, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        val back = (tail - tip) / (tail - tip).getDistance() * 9.dp.toPx()
        val side = Offset(-back.y, back.x)
        drawLine(accent, tip + back + side, tip, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        drawLine(accent, tip + back - side, tip, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        val unitAt = if (name is VanName.Ramp) {
            // From the cross street, on the traffic's right, curving onto the highway with it.
            val joinB = roadB + frame.acrossFraction(band * 0.45f)
            val p0 = frame.at(crossA, 1.3f)
            val p1 = frame.at(crossA, 0.35f)
            val p2 = frame.at(crossA + 0.2f, joinB)
            val p3 = frame.at(0.25f, joinB)
            val ramp = Path().apply {
                moveTo(p0.x, p0.y)
                cubicTo(p1.x, p1.y, p2.x, p2.y, p3.x, p3.y)
            }
            drawPath(ramp, road, style = Stroke(width = 18.dp.toPx(), cap = StrokeCap.Round))
            drawPath(ramp, accent, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 8.dp.toPx()))))
            bezier(p0, p1, p2, p3, 0.55f)
        } else {
            frame.at(if (crossLabel != null) crossA + 0.35f else 0f, laneB)
        }
        drawCircle(Color.White, radius = 17.dp.toPx(), center = unitAt)
        drawCircle(pinColor, radius = 14.dp.toPx(), center = unitAt)
        drawCircle(Color.White, radius = 2.5.dp.toPx(), center = unitAt)
        // Labels stay upright: the highway's on its far side from the ramp, the cross street's beside it.
        label(measurer, mainLabel, labelStyle, frame.at(0.15f, roadB - frame.acrossFraction(band * 0.5f + 16.dp.toPx())))
        if (crossLabel != null) label(measurer, crossLabel, labelStyle, frame.at(crossA - 0.25f, 0.7f))
        label(measurer, unitLabel, unitStyle, unitAt + Offset(0f, 30.dp.toPx()))
    }
}

/**
 * The canvas seen from the traffic: [at] places a point [along] the way it goes and [across] to its right, each
 * -1..1 over the canvas's half-width and half-height, whichever way [heading] lies on screen (north is up).
 */
private class TrafficFrame(private val heading: Heading, private val center: Offset, private val halfWidth: Float, private val halfHeight: Float) {
    fun at(along: Float, across: Float): Offset = when (heading) {
        Heading.EAST -> Offset(center.x + along * halfWidth, center.y + across * halfHeight)
        Heading.WEST -> Offset(center.x - along * halfWidth, center.y - across * halfHeight)
        Heading.SOUTH -> Offset(center.x - across * halfWidth, center.y + along * halfHeight)
        Heading.NORTH -> Offset(center.x + across * halfWidth, center.y - along * halfHeight)
    }

    /** [pixels] across the traffic, as a fraction of the half-size across it. */
    fun acrossFraction(pixels: Float): Float = pixels / if (heading == Heading.EAST || heading == Heading.WEST) halfHeight else halfWidth
}

/** The cubic Bézier through [p0]..[p3] at [t]. */
private fun bezier(p0: Offset, p1: Offset, p2: Offset, p3: Offset, t: Float): Offset {
    val u = 1 - t
    return p0 * (u * u * u) + p1 * (3 * u * u * t) + p2 * (3 * u * t * t) + p3 * (t * t * t)
}

/** [text] centred on [at], kept inside the canvas. */
private fun DrawScope.label(measurer: TextMeasurer, text: String, style: TextStyle, at: Offset) {
    val layout = measurer.measure(text, style, maxLines = 1)
    var x = (at.x - layout.size.width / 2f).coerceIn(8.dp.toPx(), (size.width - layout.size.width - 8.dp.toPx()).coerceAtLeast(8.dp.toPx()))
    val y = (at.y - layout.size.height / 2f).coerceIn(4.dp.toPx(), size.height - layout.size.height - 20.dp.toPx())
    // Clear of the back button in the top left corner.
    val button = 72.dp.toPx() + WindowStatusAllowance.toPx()
    if (x < button && y < button) x = button
    drawText(layout, topLeft = Offset(x, y))
}

private val DIAGRAM_HEIGHT = 220.dp

/** Room for the status bar above the back button, which sits under it. */
private val WindowStatusAllowance = 8.dp

