package io.cosgriff.waitlight.domain

import java.time.Instant
import java.time.LocalDate

/** The line under the place: when the van page was fetched, always with its date. `:app` words it. */
sealed interface VanCheckedLine {
    /** Live: "Status checked Nov 20, 5:57 PM". */
    data class StatusChecked(val at: Instant) : VanCheckedLine

    /** The weekend: "Schedule checked Nov 21, 10:05 AM". */
    data class ScheduleChecked(val at: Instant) : VanCheckedLine

    /** Stale or broken, the statuses not to be gone by: "Schedule from Nov 20, 4:52 PM". */
    data class ScheduleFrom(val at: Instant) : VanCheckedLine

    /** No schedule yet, the first fetch under way: "Getting van sites…". */
    data object Getting : VanCheckedLine
}

/** The banner under the header. */
sealed interface VanBanner {
    /** Saturday or Sunday: next week is shown. */
    data object Weekend : VanBanner

    /** The last fetch failed and the statuses are too old: the schedule from [since] still shows. */
    data class Stale(val since: Instant) : VanBanner

    /** The page couldn't be read: the schedule from [since], every status "Scheduled". */
    data class Broken(val since: Instant) : VanBanner
}

/** The line above the day's rows. */
sealed interface VanSummary {
    /** A day with no sites, or none posted: just its date, "Thu, Nov 26"; the card under it says why. */
    data class Date(val date: LocalDate) : VanSummary

    /** Today, live status unreadable: "4 sites today · live status unavailable". */
    data class TodayUnavailable(val sites: Int) : VanSummary

    /** Today, the "Reading now" filter on: "1 of 4 sites reading cars now". */
    data class ReadingOnly(val reading: Int, val sites: Int) : VanSummary

    /** Today: "4 sites today · 1 reading now". */
    data class Today(val sites: Int, val reading: Int) : VanSummary

    /** Another day: "Monday · 3 sites · live status on the day". */
    data class Day(val date: LocalDate, val sites: Int) : VanSummary
}

/** The "Reading now" chip: whether it shows, and whether it's on (and so filters the rows). */
data class ReadingFilter(val shown: Boolean, val selected: Boolean)

/** How a row's status reads and is heard. */
sealed interface VanRowStatus {
    /** Its [status] as is. */
    data class Plain(val status: VanStatus) : VanRowStatus

    /** Today with the page unreadable: "Scheduled today", so it isn't taken for live. */
    data object ScheduledToday : VanRowStatus

    /** Another day: "Scheduled", heard as "Scheduled Monday". */
    data class ScheduledOn(val date: LocalDate) : VanRowStatus
}

/** A row of the list and how its status reads. */
data class VanListRow(val row: VanRow, val status: VanRowStatus)

/** Everything the vans list's header, banner, hero, chips and rows decide, typed; `:app` only words it. */
data class VanListHeader(
    /** Where "here" is, as on the wait list ([placeLine]). */
    val place: PlaceLine,
    val checked: VanCheckedLine?,
    val banner: VanBanner?,
    /** Whether the refresh button shows: only after a failed fetch or with no schedule yet. */
    val retry: Boolean,
    /** The day on screen: the one picked, else the first; null with no days. */
    val day: VanDay?,
    val summary: VanSummary?,
    val filter: ReadingFilter,
    /** "Nearest van reading cars now", only while [VanListState.LIVE] ([nearestReading]). */
    val hero: VanRow?,
    /** The day's rows, after the filter. */
    val rows: List<VanListRow>,
)

/**
 * The vans list's header for [list] with the day chip [selected] (null for the first), the "Reading now" filter
 * [readingOnly], "here" and whether a fetch is under way.
 */
fun vanListHeader(
    list: VanList,
    selected: LocalDate?,
    readingOnly: Boolean,
    here: Here?,
    locationAllowed: Boolean,
    refreshing: Boolean,
): VanListHeader {
    val state = list.state
    val day = list.days.firstOrNull { it.date == selected } ?: list.days.firstOrNull()
    val sites = (day?.content as? VanDayContent.Sites)?.rows.orEmpty()
    val reading = readingCount(day)
    // Today only, while live status can be read.
    val filterApplies = day?.isToday == true && state != VanListState.BROKEN
    val filterOn = readingOnly && filterApplies
    val rows = if (filterOn) sites.filter { it.status == VanStatus.Reading } else sites
    return VanListHeader(
        place = placeLine(here, locationAllowed),
        checked = checkedLine(list, refreshing),
        banner = when (state) {
            VanListState.WEEKEND -> VanBanner.Weekend
            VanListState.STALE -> list.fetchedAt?.let { VanBanner.Stale(it) }
            VanListState.BROKEN -> list.fetchedAt?.let { VanBanner.Broken(it) }
            VanListState.LIVE, VanListState.EMPTY -> null
        },
        retry = state == VanListState.STALE || state == VanListState.EMPTY,
        day = day,
        summary = day?.let { summary(it, state, filterOn, sites.size, reading) },
        // Only while something is reading, or to turn the filter back off.
        filter = ReadingFilter(shown = filterApplies && (reading > 0 || readingOnly), selected = filterOn),
        hero = day?.let(::nearestReading)?.takeIf { state == VanListState.LIVE },
        rows = rows.map { VanListRow(it, vanRowStatus(it, state, day?.isToday == true)) },
    )
}

/** How [row]'s status reads on a day that [isToday] or not, with the list in [state]. */
fun vanRowStatus(row: VanRow, state: VanListState, isToday: Boolean): VanRowStatus = when {
    row.status != VanStatus.Scheduled -> VanRowStatus.Plain(row.status)
    isToday && state == VanListState.BROKEN -> VanRowStatus.ScheduledToday
    !isToday -> VanRowStatus.ScheduledOn(row.date)
    else -> VanRowStatus.Plain(row.status)
}

private fun checkedLine(list: VanList, refreshing: Boolean): VanCheckedLine? {
    val at = list.fetchedAt ?: return if (refreshing) VanCheckedLine.Getting else null
    return when (list.state) {
        VanListState.LIVE -> VanCheckedLine.StatusChecked(at)
        VanListState.WEEKEND -> VanCheckedLine.ScheduleChecked(at)
        VanListState.STALE, VanListState.BROKEN, VanListState.EMPTY -> VanCheckedLine.ScheduleFrom(at)
    }
}

private fun summary(day: VanDay, state: VanListState, filterOn: Boolean, sites: Int, reading: Int): VanSummary = when {
    day.content !is VanDayContent.Sites -> VanSummary.Date(day.date)
    day.isToday && state == VanListState.BROKEN -> VanSummary.TodayUnavailable(sites)
    day.isToday && filterOn -> VanSummary.ReadingOnly(reading, sites)
    day.isToday -> VanSummary.Today(sites, reading)
    else -> VanSummary.Day(day.date, sites)
}
