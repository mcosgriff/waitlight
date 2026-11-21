package io.cosgriff.waitlight.ui.vans

import android.content.ActivityNotFoundException
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AddTask
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.cosgriff.waitlight.R
import io.cosgriff.waitlight.data.vans.VanClient
import io.cosgriff.waitlight.domain.ReadingFilter
import io.cosgriff.waitlight.domain.VanBanner
import io.cosgriff.waitlight.domain.VanCheckedLine
import io.cosgriff.waitlight.domain.VanDay
import io.cosgriff.waitlight.domain.VanDayContent
import io.cosgriff.waitlight.domain.VanListState
import io.cosgriff.waitlight.domain.VanRow
import io.cosgriff.waitlight.domain.VanRowStatus
import io.cosgriff.waitlight.domain.VanSummary
import io.cosgriff.waitlight.domain.nearestFarAway
import io.cosgriff.waitlight.ui.TimeText
import io.cosgriff.waitlight.ui.components.DriveTimePill
import io.cosgriff.waitlight.ui.components.PlaceLine
import io.cosgriff.waitlight.ui.components.VanBadge
import io.cosgriff.waitlight.ui.components.rowStatusText
import io.cosgriff.waitlight.ui.components.vanHoursText
import io.cosgriff.waitlight.ui.components.vanNameSpoken
import io.cosgriff.waitlight.ui.components.vanNameText
import io.cosgriff.waitlight.ui.components.vanStatusColor
import io.cosgriff.waitlight.ui.theme.BannerColors
import io.cosgriff.waitlight.ui.theme.waitColors
import java.time.LocalDate

@Composable
fun VansScreen(onOpenSite: (key: String, date: LocalDate) -> Unit, onChangeLocation: () -> Unit, onOpenDriveBys: () -> Unit, onOpenVehicles: () -> Unit) {
    val viewModel: VansViewModel = hiltViewModel()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    VansContent(
        ui = ui,
        onPick = viewModel::pick,
        onToggleReading = viewModel::toggleReadingOnly,
        onRefresh = viewModel::refresh,
        onOpenSite = onOpenSite,
        onChangeLocation = onChangeLocation,
        onOpenDriveBys = onOpenDriveBys,
        onOpenVehicles = onOpenVehicles,
        onOpenVanPage = {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, VanClient.PAGE_URL.toUri()))
            } catch (e: ActivityNotFoundException) {
                Log.i("Waitlight", "No browser for the van page", e)
            }
        },
    )
}

/** The vans list, as drawn in `design/vans/source/Main.dc.html` and the `Vans*` boards. */
@Composable
fun VansContent(
    ui: VansUi,
    onPick: (LocalDate) -> Unit,
    onToggleReading: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSite: (key: String, date: LocalDate) -> Unit,
    onChangeLocation: () -> Unit,
    onOpenVanPage: () -> Unit,
    onOpenDriveBys: () -> Unit = {},
    onOpenVehicles: () -> Unit = {},
) {
    var showHow by rememberSaveable { mutableStateOf(false) }
    val header = ui.header
    val day = header.day
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "header") { Header(ui, onInfo = { showHow = true }, onRefresh = onRefresh, onChangeLocation = onChangeLocation) }
            item(key = "drive-bys") { DriveBysLine(ui, onOpenDriveBys) }
            item(key = "vehicles") { VehiclesLine(ui.vehicles, onOpenVehicles) }
            header.banner?.let { banner -> item(key = "banner") { Banner(banner) } }
            header.hero?.let { hero -> item(key = "hero") { HeroCard(hero, onOpenSite) } }
            if (ui.list.days.isNotEmpty()) item(key = "days") { DayChips(ui.list.days, shown = day?.date, onPick) }
            header.summary?.let { summary -> item(key = "summary") { SummaryLine(summary, header.filter, onToggleReading) } }
            nearestFarAway(day)?.let { minutes -> item(key = "far") { FarAwayNote(minutes) } }
            when {
                ui.state == VanListState.EMPTY -> item(key = "empty") {
                    EmptyCard(stringResource(R.string.vans_empty_title), stringResource(R.string.vans_empty_body))
                }
                day?.content is VanDayContent.NoSites -> item(key = "none") {
                    val holiday = (day.content as VanDayContent.NoSites).holiday != null
                    EmptyCard(
                        stringResource(R.string.vans_none_title, TimeText.longDate(day.date)),
                        stringResource(if (holiday) R.string.vans_none_body_holiday else R.string.vans_none_body),
                    )
                }
                day?.content == VanDayContent.NotPosted -> item(key = "not-posted") {
                    EmptyCard(
                        stringResource(R.string.vans_not_posted_title, TimeText.weekday(day.date)),
                        stringResource(R.string.vans_not_posted_body),
                    )
                }
            }
            items(header.rows, key = { "${it.row.site.key}@${it.row.date}" }) { item ->
                val row = item.row
                VanRowItem(row, item.status, onClick = { onOpenSite(row.site.key, row.date) })
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            stringResource(R.string.vans_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 10.dp),
        )
    }
    if (showHow) HowItWorksSheet(onDismiss = { showHow = false }, onOpenVanPage = onOpenVanPage)
}

@Composable
private fun Header(ui: VansUi, onInfo: () -> Unit, onRefresh: () -> Unit, onChangeLocation: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.statusBarsPadding().padding(start = 20.dp, end = 16.dp, top = 20.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.vans_title),
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp, letterSpacing = (-0.3).sp),
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            // Retry only where it helps: a failed fetch, or no schedule yet.
            if (ui.header.retry) {
                IconButton(
                    onClick = onRefresh,
                    enabled = !ui.refreshing,
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                ) { Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.vans_refresh)) }
            }
            IconButton(
                onClick = onInfo,
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            ) { Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.vans_info)) }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 2.dp),
        ) {
            PlaceLine(ui.header.place, onChangeLocation)
        }
        // On its own line: with its date it doesn't fit beside the place.
        ui.header.checked?.let { line ->
            val checked = checked(line)
            Text(checked, style = MaterialTheme.typography.bodyMedium, color = muted, modifier = Modifier.padding(start = 22.dp))
        }
        Text(
            stringResource(R.string.vans_pitch),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            color = muted,
            modifier = Modifier.padding(start = 22.dp, top = 2.dp),
        )
        if (ui.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp, end = 4.dp))
    }
}

/** "Status checked Nov 20, 4:52 PM", or how old the schedule is when its statuses can't be gone by. */
@Composable
private fun checked(line: VanCheckedLine): String = when (line) {
    is VanCheckedLine.StatusChecked -> stringResource(R.string.vans_status_checked, TimeText.dateTime(line.at))
    is VanCheckedLine.ScheduleChecked -> stringResource(R.string.vans_schedule_checked, TimeText.dateTime(line.at))
    is VanCheckedLine.ScheduleFrom -> stringResource(R.string.vans_schedule_from, TimeText.dateTime(line.at))
    VanCheckedLine.Getting -> stringResource(R.string.vans_getting)
}

@Composable
private fun Banner(banner: VanBanner) {
    val colors = MaterialTheme.waitColors
    val (look: BannerColors, icon: ImageVector) = when (banner) {
        VanBanner.Weekend -> colors.info to Icons.Rounded.CalendarMonth
        is VanBanner.Stale -> colors.warning to Icons.Rounded.History
        is VanBanner.Broken -> colors.error to Icons.Rounded.ErrorOutline
    }
    val (title, body) = when (banner) {
        VanBanner.Weekend -> stringResource(R.string.vans_weekend_title) to stringResource(R.string.vans_weekend_body)
        is VanBanner.Stale -> stringResource(R.string.vans_stale_title) to stringResource(R.string.vans_stale_body, TimeText.dateTime(banner.since))
        is VanBanner.Broken -> stringResource(R.string.vans_broken_title) to stringResource(R.string.vans_broken_body, TimeText.dateTime(banner.since))
    }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = look.container,
        contentColor = look.content,
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(top = 1.dp).size(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
                Text(body, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 19.sp))
            }
        }
    }
}

@Composable
private fun HeroCard(hero: VanRow, onOpenSite: (String, LocalDate) -> Unit) {
    val colors = MaterialTheme.waitColors
    val drive = hero.driveMinutes ?: return
    val name = vanNameText(hero.site.name)
    val openLabel = stringResource(R.string.vans_hero_open, vanNameSpoken(hero.site.name))
    Surface(
        onClick = { onOpenSite(hero.site.key, hero.date) },
        shape = MaterialTheme.shapes.large,
        color = colors.hero,
        contentColor = Color.White,
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(52.dp).background(colors.short.container, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Sensors, contentDescription = null, tint = colors.short.content, modifier = Modifier.size(26.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.vans_hero_label).uppercase(),
                    color = colors.heroLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp,
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(name, style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp))
                Text(
                    stringResource(R.string.vans_hero_detail, hero.place, drive, TimeText.time(hero.site.closes)),
                    color = colors.heroDetail,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Box(Modifier.size(44.dp).background(Color.White.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = openLabel)
            }
        }
    }
}

@Composable
private fun DayChips(days: List<VanDay>, shown: LocalDate?, onPick: (LocalDate) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        days.forEach { day -> DayChip(day, selected = day.date == shown, onClick = { onPick(day.date) }) }
    }
}

@Composable
private fun DayChip(day: VanDay, selected: Boolean, onClick: () -> Unit) {
    val top = if (day.isToday) stringResource(R.string.vans_chip_today) else TimeText.shortWeekday(day.date)
    val bottom = if (day.isToday) TimeText.weekdayAndDay(day.date) else TimeText.dayOfMonth(day.date)
    val description = if (day.isToday) "$top, ${TimeText.longDate(day.date)}" else TimeText.longDate(day.date)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clearAndSetSemantics { contentDescription = description; this.selected = selected; role = Role.Tab },
    ) {
        Column(
            Modifier.heightIn(min = 48.dp).padding(horizontal = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(top, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 15.sp, style = MaterialTheme.typography.labelLarge)
            Text(bottom, fontSize = 11.sp, lineHeight = 13.sp, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SummaryLine(summary: VanSummary, filter: ReadingFilter, onToggleReading: () -> Unit) {
    val text = when (summary) {
        is VanSummary.Date -> TimeText.shortDate(summary.date)
        is VanSummary.TodayUnavailable -> stringResource(
            R.string.vans_summary_join,
            pluralStringResource(R.plurals.vans_summary_sites_today, summary.sites, summary.sites),
            stringResource(R.string.vans_summary_unavailable),
        )
        is VanSummary.ReadingOnly -> pluralStringResource(R.plurals.vans_summary_reading_only, summary.sites, summary.reading, summary.sites)
        is VanSummary.Today -> stringResource(
            R.string.vans_summary_join,
            pluralStringResource(R.plurals.vans_summary_sites_today, summary.sites, summary.sites),
            pluralStringResource(R.plurals.vans_summary_reading, summary.reading, summary.reading),
        )
        is VanSummary.Day -> pluralStringResource(R.plurals.vans_summary_day, summary.sites, TimeText.weekday(summary.date), summary.sites)
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        if (filter.shown) {
            FilterChip(
                selected = filter.selected,
                onClick = onToggleReading,
                label = { Text(stringResource(R.string.vans_filter_reading), style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium)) },
                leadingIcon = { Icon(Icons.Rounded.Sensors, contentDescription = null, modifier = Modifier.size(16.dp)) },
                shape = RoundedCornerShape(50),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    labelColor = MaterialTheme.colorScheme.onSurface,
                    iconColor = MaterialTheme.colorScheme.onSurface,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = filter.selected,
                    borderColor = MaterialTheme.colorScheme.outline,
                    selectedBorderColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}

@Composable
private fun EmptyCard(title: String, body: String) {
    val colors = MaterialTheme.waitColors
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(44.dp).background(colors.stale.container, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Event, contentDescription = null, tint = colors.stale.content, modifier = Modifier.size(22.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp))
                Text(body, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun VanRowItem(row: VanRow, rowStatus: VanRowStatus, onClick: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val status = rowStatusText(rowStatus)
    val spokenStatus = rowStatusText(rowStatus, spoken = true)
    val badgeDescription = stringResource(R.string.van_badge_desc, spokenStatus, vanNameSpoken(row.site.name))
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth(),
    ) {
        Row(
            Modifier.heightIn(min = 72.dp).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            VanBadge(row.status, badgeDescription, 48.dp, ringColor = MaterialTheme.colorScheme.surface)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(vanNameText(row.site.name), style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, lineHeight = 20.sp))
                Text(status, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = vanStatusColor(row.status))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.van_row_place_hours, row.place, vanHoursText(row.site.opens, row.site.closes)),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (row.site.twoUnits) {
                        Text(
                            stringResource(R.string.van_two_units),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            row.drive?.let { DriveTimePill(it) }
        }
    }
}

/** "How roadside vans work", as drawn in `design/vans/source/HowItWorks.dc.html`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HowItWorksSheet(onDismiss: () -> Unit, onOpenVanPage: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        HowItWorksContent(onDismiss, onOpenVanPage)
    }
}

@Composable
internal fun HowItWorksContent(onDone: () -> Unit, onOpenVanPage: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier.padding(start = 22.dp, end = 22.dp, bottom = 22.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.vans_how_title), style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold), modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.vans_how_intro), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp), color = muted)
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HowStep(null, stringResource(R.string.vans_how_two))
                HowStep(Icons.Rounded.CalendarMonth, stringResource(R.string.vans_how_window))
                HowStep(Icons.Rounded.Schedule, stringResource(R.string.vans_how_when))
            }
        }
        Text(stringResource(R.string.vans_how_private), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 19.sp), color = muted)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onOpenVanPage, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Text(stringResource(R.string.vans_how_page), color = MaterialTheme.colorScheme.onSurface)
            }
            Button(onClick = onDone, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Text(stringResource(R.string.vans_how_done))
            }
        }
    }
}

@Composable
private fun HowStep(icon: ImageVector?, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(28.dp).background(MaterialTheme.colorScheme.primary, CircleShape).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
            if (icon == null) {
                Text(stringResource(R.string.vans_how_two_badge), color = MaterialTheme.colorScheme.onPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            } else {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
            }
        }
        Text(text, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp))
    }
}

/** "My drive-bys · 2 logged in the last 14 months", opening the log. */
@Composable
private fun DriveBysLine(ui: VansUi, onOpen: () -> Unit) {
    val count = ui.driveBys.inWindow
    val detail = if (ui.driveBys.entries.isEmpty()) {
        stringResource(R.string.driveby_summary_none)
    } else {
        pluralStringResource(R.plurals.driveby_summary, count, count)
    }
    RecordLine(Icons.Outlined.AddTask, stringResource(R.string.driveby_list_title), detail, onOpen)
}

/** "My vehicles · 2 vehicles", opening them: each one's window and reminders. */
@Composable
private fun VehiclesLine(count: Int, onOpen: () -> Unit) {
    val detail = if (count == 0) stringResource(R.string.vans_vehicles_none) else pluralStringResource(R.plurals.vans_vehicles_count, count, count)
    RecordLine(Icons.Outlined.DirectionsCar, stringResource(R.string.vans_vehicles_title), detail, onOpen)
}

@Composable
private fun RecordLine(icon: ImageVector, title: String, detail: String, onOpen: () -> Unit) {
    Surface(
        onClick = onOpen,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
    ) {
        Row(
            Modifier.heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
                Text(detail, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The nearest van is out of the way: a station test may be quicker. */
@Composable
private fun FarAwayNote(minutes: Int) {
    val info = MaterialTheme.waitColors.info
    Surface(shape = MaterialTheme.shapes.medium, color = info.container, contentColor = info.content, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.padding(top = 1.dp).size(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(pluralStringResource(R.plurals.vans_far_title, minutes, minutes), style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
                Text(stringResource(R.string.vans_far_body), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 19.sp))
            }
        }
    }
}
