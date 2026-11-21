package io.cosgriff.waitlight.ui.vans

import android.content.ActivityNotFoundException
import android.content.Intent
import android.util.Log
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
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import io.cosgriff.waitlight.domain.VanName
import io.cosgriff.waitlight.domain.VanStatus
import io.cosgriff.waitlight.domain.VanSiteWeek
import io.cosgriff.waitlight.domain.AirCareHours
import io.cosgriff.waitlight.domain.currentVanWeek
import io.cosgriff.waitlight.ui.TimeText
import io.cosgriff.waitlight.ui.components.VanBadge
import io.cosgriff.waitlight.ui.components.vanBadgeLook
import io.cosgriff.waitlight.ui.components.vanHoursText
import io.cosgriff.waitlight.ui.components.vanNameSpoken
import io.cosgriff.waitlight.ui.components.vanNameText
import io.cosgriff.waitlight.ui.components.vanStatusColor
import io.cosgriff.waitlight.ui.theme.waitColors
import java.time.LocalDate

@Composable
fun VanDetailScreen(key: String, date: LocalDate, onBack: () -> Unit) {
    val viewModel: VanDetailViewModel = hiltViewModel()
    LaunchedEffect(key, date) { viewModel.show(key, date) }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val current = ui ?: return
    val row = current.row ?: return
    val context = LocalContext.current
    val chooserTitle = stringResource(R.string.van_detail_directions_chooser)
    val label = vanNameSpoken(row.site.name)
    VanDetailContent(
        ui = current,
        onBack = onBack,
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
fun VanDetailContent(ui: VanDetailUi, onBack: () -> Unit, onDirections: () -> Unit) {
    val row = ui.row ?: return
    val site = row.site
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val background = MaterialTheme.colorScheme.background
    val status = rowStatusText(row, ui.state, ui.isToday)
    Column(
        Modifier
            .fillMaxSize()
            .background(background)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding(),
    ) {
        Box(Modifier.fillMaxWidth().height(DIAGRAM_HEIGHT)) {
            SiteDiagram(site.name, row.status, Modifier.matchParentSize())
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
                VanBadge(row.status, status, 64.dp)
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        vanNameText(site.name),
                        style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp),
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(status, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = vanStatusColor(row.status))
                    val hours = if (ui.isToday && row.status != VanStatus.Done) {
                        stringResource(R.string.van_detail_until, TimeText.time(site.closes))
                    } else {
                        vanHoursText(site.opens, site.closes)
                    }
                    val small = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
                    val line = row.driveMinutes?.let { stringResource(R.string.van_detail_drive, row.place, it, hours) }
                        ?: stringResource(R.string.van_detail_place, row.place, hours)
                    Text(line, style = small, color = muted)
                    if (ui.isToday) {
                        ui.fetchedAt?.let { Text(stringResource(R.string.van_detail_checked, TimeText.timeSince(it, ui.now)), style = small, color = muted) }
                    }
                }
            }

            Button(onClick = onDirections, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Icon(Icons.Outlined.Navigation, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.van_detail_directions), modifier = Modifier.padding(start = 8.dp), fontSize = 16.sp)
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

            ui.week?.let { week -> WeekCard(week, today = ui.now.atZone(AirCareHours.zone).toLocalDate()) }

            Text(stringResource(R.string.van_detail_footer), style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp), color = muted)
        }
    }
}

@Composable
private fun WeekCard(week: VanSiteWeek, today: LocalDate) {
    val nextMonday = currentVanWeek(today).plusWeeks(1)
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            Text(
                stringResource(R.string.van_detail_week_title),
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
            )
            week.visits.forEach { visit ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val isToday = visit.date == today
                val day = if (isToday) stringResource(R.string.van_detail_today, TimeText.shortDate(visit.date)) else TimeText.shortDate(visit.date)
                WeekLine(day, vanHoursText(visit.opens, visit.closes), bold = isToday, muted = visit.date < today)
            }
            if (week.nextWeekPosted && week.visits.none { it.date >= nextMonday }) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                WeekLine(stringResource(R.string.van_detail_next_week), stringResource(R.string.van_detail_not_scheduled), bold = false, muted = true)
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
 * The site drawn, not mapped: weekly sites have no bundled map (ADR-0006 is for the fixed stations). A ramp is a
 * highway across the top, the cross street down the middle and the ramp curving between, the unit on it in its
 * badge's colour; a road site is the road with the cross street and the unit just past it. The content
 * description says the same in words, and the status is in the text below, so colour is not the only signal.
 */
@Composable
private fun SiteDiagram(name: VanName, status: VanStatus, modifier: Modifier = Modifier) {
    val road = MaterialTheme.colorScheme.background
    val ground = MaterialTheme.colorScheme.surfaceContainerHighest
    val lane = MaterialTheme.colorScheme.outline
    val accent = MaterialTheme.colorScheme.primary
    val pin = vanBadgeLook(status).let { if (it.border == null) it.container else MaterialTheme.waitColors.open.container }
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
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
    Box(modifier.background(ground).clearAndSetSemantics { contentDescription = description; role = Role.Image }) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val bandTop = h * 0.30f
            val bandHeight = h * 0.26f
            drawRect(road, topLeft = Offset(0f, bandTop), size = Size(w, bandHeight))
            drawLine(lane, Offset(0f, bandTop + bandHeight / 2), Offset(w, bandTop + bandHeight / 2), strokeWidth = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14.dp.toPx(), 12.dp.toPx())))
            val crossX = w * 0.34f
            if (crossLabel != null) {
                drawRect(road, topLeft = Offset(crossX - 20.dp.toPx(), 0f), size = Size(40.dp.toPx(), h))
            }
            val unit: Offset
            if (name is VanName.Ramp) {
                val ramp = Path().apply {
                    moveTo(crossX + 12.dp.toPx(), h)
                    cubicTo(crossX + 12.dp.toPx(), h * 0.72f, crossX + 60.dp.toPx(), bandTop + bandHeight + 6.dp.toPx(), w * 0.72f, bandTop + bandHeight - 4.dp.toPx())
                }
                drawPath(ramp, road, style = Stroke(width = 20.dp.toPx(), cap = StrokeCap.Round))
                drawPath(ramp, accent, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 8.dp.toPx()))))
                unit = Offset(crossX + 48.dp.toPx(), h * 0.68f)
            } else {
                unit = Offset(if (crossLabel != null) crossX + 70.dp.toPx() else w / 2, bandTop + bandHeight * 0.75f)
            }
            // Direction of travel, along the main road.
            val arrowY = bandTop + bandHeight * 0.75f
            drawLine(accent, Offset(w * 0.78f, arrowY), Offset(w * 0.90f, arrowY), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            drawLine(accent, Offset(w * 0.88f, arrowY - 8.dp.toPx()), Offset(w * 0.90f, arrowY), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            drawLine(accent, Offset(w * 0.88f, arrowY + 8.dp.toPx()), Offset(w * 0.90f, arrowY), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(Color.White, radius = 18.dp.toPx(), center = unit)
            drawCircle(pin, radius = 15.dp.toPx(), center = unit)
            drawCircle(Color.White, radius = 2.5.dp.toPx(), center = unit)
        }
        Text(mainLabel, color = labelColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.TopStart).padding(start = 72.dp, top = DIAGRAM_HEIGHT * 0.30f - 20.dp))
        if (crossLabel != null) {
            Text(crossLabel, color = labelColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp, start = 40.dp))
        }
        Text(unitLabel, color = labelColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 52.dp))
    }
}

private val DIAGRAM_HEIGHT = 220.dp
