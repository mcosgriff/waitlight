package io.cosgriff.waitlight.ui.list

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.cosgriff.waitlight.R
import io.cosgriff.waitlight.domain.AirCareHours
import io.cosgriff.waitlight.domain.Badge
import io.cosgriff.waitlight.domain.Here
import io.cosgriff.waitlight.domain.ListState
import io.cosgriff.waitlight.domain.OpeningDay
import io.cosgriff.waitlight.domain.SortOrder
import io.cosgriff.waitlight.domain.StationRow
import io.cosgriff.waitlight.domain.WaitReading
import io.cosgriff.waitlight.domain.WaitThresholds
import io.cosgriff.waitlight.domain.openingDay
import io.cosgriff.waitlight.ui.TimeText
import io.cosgriff.waitlight.ui.components.BadgeSize
import io.cosgriff.waitlight.ui.components.WaitBadge
import io.cosgriff.waitlight.ui.components.forBand
import io.cosgriff.waitlight.ui.theme.BannerColors
import io.cosgriff.waitlight.ui.theme.waitColors
import java.time.Duration
import java.time.Instant

@Composable
fun StationListScreen(onOpenStation: (Int) -> Unit, onChangeLocation: () -> Unit) {
    val viewModel: StationListViewModel = hiltViewModel()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    StationListContent(
        ui = ui,
        onRefresh = viewModel::refresh,
        onSort = viewModel::sortBy,
        onToggleRims = viewModel::toggleRims,
        onOpenStation = onOpenStation,
        onChangeLocation = onChangeLocation,
    )
}

/** The list of stations, as drawn in `design/png/Main.png` and the `State*` PNGs. */
@Composable
fun StationListContent(
    ui: StationListUi,
    onRefresh: () -> Unit,
    onSort: (SortOrder) -> Unit,
    onToggleRims: () -> Unit,
    onOpenStation: (Int) -> Unit,
    onChangeLocation: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "header") { Header(ui, onRefresh, onChangeLocation) }
            when {
                ui.state == ListState.CLOSED -> item(key = "closed") { ClosedCard(ui.now) }
                ui.hero != null -> item(key = "hero") { HeroCard(ui.hero, onOpenStation) }
            }
            banner(ui)?.let { banner ->
                val asOf = ui.newestUpdate?.let { TimeText.timeSince(it, ui.now) }
                item(key = "banner") { BannerCard(banner, asOf, onRefresh) }
            }
            item(key = "chips") { Chips(ui, onSort, onToggleRims) }
            item(key = "legend") { Legend(ui) }
            items(ui.rows, key = { it.station.id }) { row ->
                StationRowItem(row, ui, onClick = { onOpenStation(row.station.id) })
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            stringResource(R.string.list_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 18.dp),
        )
    }
}

@Composable
private fun Header(ui: StationListUi, onRefresh: () -> Unit, onChangeLocation: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.statusBarsPadding().padding(start = 20.dp, end = 16.dp, top = 20.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.list_title),
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp, letterSpacing = (-0.3).sp),
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            RefreshIconButton(ui, onRefresh)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 2.dp),
        ) {
            PlaceLine(ui, onChangeLocation)
            // For the eye only.
            Text("·", color = muted, modifier = Modifier.clearAndSetSemantics {})
            Text(updatedText(ui), style = MaterialTheme.typography.bodyMedium, color = muted)
        }
        val (next, nextColor) = nextText(ui)
        if (next != null) {
            Text(
                next,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                color = nextColor,
                modifier = Modifier.padding(start = 22.dp, top = 2.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (ui.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp, end = 4.dp))
    }
}

/**
 * Where "here" is: the place for the phone's fix or the typed ZIP code ("Arvada, CO 80003"), "Near you" for a fix
 * near no Colorado ZIP, "Finding your location…" while waiting for a fix, or "Location off". A ZIP code and
 * "Location off" open the location prompt, to change or clear the ZIP or turn location on.
 */
@Composable
private fun PlaceLine(ui: StationListUi, onChangeLocation: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val here = ui.here
    val placeText = here?.place?.let { stringResource(R.string.list_place, it.place, it.zip) }
    when {
        here is Here.Zip ->
            PromptLink(Icons.Outlined.LocationOn, placeText.orEmpty(), stringResource(R.string.list_change_zip), onChangeLocation)
        here != null || ui.locationAllowed -> {
            Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = muted, modifier = Modifier.size(16.dp))
            val text = when {
                placeText != null -> placeText
                here != null -> stringResource(R.string.list_near_you)
                else -> stringResource(R.string.list_finding_location)
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, color = muted)
        }
        else ->
            PromptLink(
                Icons.Outlined.LocationOff,
                stringResource(R.string.list_location_off),
                stringResource(R.string.list_location_turn_on),
                onChangeLocation,
            )
    }
}

/** A tappable place line, in the primary colour, that opens the location prompt. */
@Composable
private fun PromptLink(icon: ImageVector, text: String, clickLabel: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.heightIn(min = 48.dp).clickable(onClickLabel = clickLabel, onClick = onClick),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun RefreshIconButton(ui: StationListUi, onRefresh: () -> Unit) {
    val description = when {
        ui.refresh.enabled -> stringResource(R.string.list_refresh)
        ui.state == ListState.CLOSED -> stringResource(R.string.list_refresh_closed)
        else -> stringResource(R.string.list_refresh_current)
    }
    val icon: @Composable () -> Unit = { Icon(Icons.Rounded.Refresh, contentDescription = description) }
    if (ui.refresh.emphasized) {
        FilledIconButton(onClick = onRefresh, enabled = ui.refresh.enabled, modifier = Modifier.size(48.dp), content = icon)
    } else {
        IconButton(
            onClick = onRefresh,
            enabled = ui.refresh.enabled && !ui.refreshing,
            modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            content = icon,
        )
    }
}

@Composable
private fun updatedText(ui: StationListUi): String {
    val newest = ui.newestUpdate
    return when {
        ui.state == ListState.CLOSED ->
            stringResource(R.string.list_closed_since, TimeText.time(AirCareHours.lastClosing(ui.now)))
        newest == null && ui.state == ListState.DUE -> stringResource(R.string.list_getting_waits)
        newest == null -> stringResource(R.string.list_no_waits_yet)
        ui.state == ListState.LIVE || ui.state == ListState.DUE -> {
            val minutes = Duration.between(newest, ui.now).toMinutes().coerceAtLeast(0).toInt()
            if (minutes == 0) stringResource(R.string.list_updated_just_now)
            else pluralStringResource(R.plurals.list_updated_minutes, minutes, minutes)
        }
        else -> stringResource(R.string.list_last_updated, TimeText.timeSince(newest, ui.now))
    }
}

/** The line under the header, and its colour. */
@Composable
private fun nextText(ui: StationListUi): Pair<String?, Color> {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val newest = ui.newestUpdate
    return when (ui.state) {
        ListState.CLOSED -> stringResource(R.string.list_waits_return) to muted
        ListState.LIVE -> ui.refresh.nextUpdateInMinutes?.let { pluralStringResource(R.plurals.list_next_update, it, it) } to muted
        ListState.DUE ->
            if (newest == null) null to muted
            else stringResource(R.string.list_new_data) to MaterialTheme.colorScheme.primary
        ListState.STALE, ListState.OFFLINE ->
            (newest?.let { age(Duration.between(it, ui.now)) }) to MaterialTheme.waitColors.moderate.icon
        ListState.BROKEN -> stringResource(R.string.list_waits_unavailable) to MaterialTheme.colorScheme.error
    }
}

@Composable
private fun age(age: Duration): String {
    val minutes = age.toMinutes().toInt()
    val hours = age.toHours().toInt()
    val days = age.toDays().toInt()
    return when {
        days > 0 -> pluralStringResource(R.plurals.list_age_days, days, days)
        hours > 0 -> pluralStringResource(R.plurals.list_age_hours, hours, hours)
        else -> pluralStringResource(R.plurals.list_age_minutes, minutes, minutes)
    }
}

@Composable
private fun HeroCard(hero: StationRow, onOpenStation: (Int) -> Unit) {
    val colors = MaterialTheme.waitColors
    val total = hero.totalMinutes ?: return
    val drive = hero.driveMinutes ?: return
    val reading = hero.reading ?: return
    val band = (hero.badge as? Badge.Live)?.band ?: return
    val badgeColors = colors.forBand(band)
    val openLabel = stringResource(R.string.list_hero_open, hero.station.name)
    Surface(
        onClick = { onOpenStation(hero.station.id) },
        shape = MaterialTheme.shapes.large,
        color = colors.hero,
        contentColor = Color.White,
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val totalDescription = pluralStringResource(R.plurals.list_hero_desc, total, total)
            Column(
                Modifier
                    .size(56.dp)
                    .background(badgeColors.container, CircleShape)
                    .clearAndSetSemantics { contentDescription = totalDescription },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(stringResource(R.string.badge_minutes, total), color = badgeColors.content, fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 20.sp)
                Text(stringResource(R.string.badge_min), color = badgeColors.content, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, lineHeight = 13.sp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.list_hero_label).uppercase(),
                    color = colors.heroLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp,
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(hero.station.name, style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp))
                val detail = when (reading) {
                    is WaitReading.Minutes -> stringResource(R.string.list_hero_detail, drive, reading.minutes)
                    is WaitReading.MoreThan -> stringResource(R.string.list_hero_detail_over, drive, reading.minutes)
                    WaitReading.NoEstimate -> return@Column
                }
                Text(detail, color = colors.heroDetail, style = MaterialTheme.typography.bodyMedium)
            }
            Box(Modifier.size(44.dp).background(Color.White.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = openLabel)
            }
        }
    }
}

@Composable
private fun ClosedCard(now: Instant) {
    val opening = AirCareHours.nextOpening(now)
    val time = TimeText.time(opening)
    val body = when (openingDay(now, opening)) {
        OpeningDay.TODAY -> stringResource(R.string.list_closed_today, time)
        OpeningDay.TOMORROW -> stringResource(R.string.list_closed_tomorrow, time)
        OpeningDay.LATER -> stringResource(R.string.list_closed_later, TimeText.weekday(opening), time)
    }
    val closed = MaterialTheme.waitColors.closed
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(56.dp).background(closed.container, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Bedtime, contentDescription = null, tint = closed.content, modifier = Modifier.size(26.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(R.string.list_closed_title), style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private enum class BannerKind { STALE, OFFLINE, BROKEN }

private fun banner(ui: StationListUi): BannerKind? = when (ui.state) {
    ListState.STALE -> BannerKind.STALE
    ListState.OFFLINE -> BannerKind.OFFLINE
    ListState.BROKEN -> BannerKind.BROKEN
    else -> null
}

/** The stale, offline and broken banners. [asOf] is the newest data's time, if there is any. */
@Composable
private fun BannerCard(kind: BannerKind, asOf: String?, onRetry: () -> Unit) {
    val colors = MaterialTheme.waitColors
    val (bannerColors: BannerColors, icon: ImageVector) = when (kind) {
        BannerKind.STALE -> colors.warning to Icons.Rounded.History
        BannerKind.OFFLINE -> colors.info to Icons.Rounded.CloudOff
        BannerKind.BROKEN -> colors.error to Icons.Rounded.ErrorOutline
    }
    val title = when (kind) {
        BannerKind.STALE -> stringResource(R.string.list_stale_title)
        BannerKind.OFFLINE -> stringResource(R.string.list_offline_title)
        BannerKind.BROKEN -> stringResource(R.string.list_broken_title)
    }
    val body = when (kind) {
        BannerKind.STALE -> asOf?.let { stringResource(R.string.list_stale_body, it) } ?: stringResource(R.string.list_stale_body_no_data)
        BannerKind.OFFLINE -> asOf?.let { stringResource(R.string.list_offline_body, it) } ?: stringResource(R.string.list_offline_body_no_data)
        BannerKind.BROKEN -> asOf?.let { stringResource(R.string.list_broken_body, it) } ?: stringResource(R.string.list_broken_body_no_data)
    }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = bannerColors.container,
        contentColor = bannerColors.content,
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(top = 1.dp).size(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
                Text(body, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 19.sp))
                // An unreadable page won't read better for asking again; the header's button still retries.
                if (kind != BannerKind.BROKEN) {
                    OutlinedButton(
                        onClick = onRetry,
                        border = BorderStroke(1.dp, bannerColors.content),
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Text(stringResource(R.string.list_try_again), color = bannerColors.content)
                    }
                }
            }
        }
    }
}

@Composable
private fun Chips(ui: StationListUi, onSort: (SortOrder) -> Unit, onToggleRims: () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
    ) {
        // Without a location the list is alphabetical whatever the chip says, so the sort chips go.
        if (ui.hasLocation) {
            val closed = ui.state == ListState.CLOSED
            WaitlightChip(stringResource(R.string.list_sort_nearest), ui.order == SortOrder.NEAREST || closed) { onSort(SortOrder.NEAREST) }
            WaitlightChip(stringResource(R.string.list_sort_fastest), ui.order == SortOrder.FASTEST_OVERALL && !closed) { onSort(SortOrder.FASTEST_OVERALL) }
        }
        WaitlightChip(stringResource(R.string.list_filter_rims), ui.rimsOnly, onToggleRims)
    }
}

@Composable
private fun WaitlightChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium)) },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outline,
            selectedBorderColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

@Composable
private fun Legend(ui: StationListUi) {
    val colors = MaterialTheme.waitColors
    val thresholds = WaitThresholds.STANDARD
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.padding(horizontal = 20.dp),
    ) {
        LegendItem(Icons.Rounded.CheckCircle, colors.short.icon, stringResource(R.string.list_legend_short, thresholds.greenMax))
        LegendItem(Icons.Rounded.Schedule, colors.moderate.icon, stringResource(R.string.list_legend_moderate, thresholds.greenMax, thresholds.yellowMax))
        LegendItem(Icons.Rounded.Warning, colors.long.icon, stringResource(R.string.list_legend_long, thresholds.yellowMax))
    }
}

@Composable
private fun LegendItem(icon: ImageVector, tint: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StationRowItem(row: StationRow, ui: StationListUi, onClick: () -> Unit) {
    val badge = row.badge
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
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
            WaitBadge(badge, BadgeSize.Row, ringColor = MaterialTheme.colorScheme.surface)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(row.station.name, style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp))
                Text(
                    stringResource(R.string.list_row_address, row.station.street, row.station.city),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (row.station.lowProfileRims) {
                    Text(
                        stringResource(R.string.list_row_rims),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .padding(top = 3.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                row.distanceMiles?.let {
                    Text(stringResource(R.string.list_row_miles, it), style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
                }
                val sub = when {
                    ui.state == ListState.CLOSED -> opensText(ui.now)
                    else -> row.driveMinutes?.let { stringResource(R.string.list_row_drive, it) }
                }
                if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = muted)
            }
        }
    }
}

@Composable
private fun opensText(now: Instant): String {
    val opening = AirCareHours.nextOpening(now)
    val time = TimeText.time(opening)
    return when (openingDay(now, opening)) {
        OpeningDay.TODAY, OpeningDay.TOMORROW -> stringResource(R.string.list_row_opens, time)
        OpeningDay.LATER -> stringResource(R.string.list_row_opens_day, TimeText.shortWeekday(opening), time)
    }
}
