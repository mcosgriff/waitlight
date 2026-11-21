package io.cosgriff.waitlight.domain

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/** Which week a van page tab is for. The page has no dates, only "Current" and "Next". */
enum class VanWeek { CURRENT, NEXT }

/** Which days a van page tab covers. */
enum class VanTabDays { MON_THU, FRIDAY }

/** A tab of the van page, from its title: "M-Th (Current Week)", "Friday (Next Week)". */
data class VanTabKind(val week: VanWeek, val days: VanTabDays) {
    /** Whether [day] is one of this tab's weekdays. */
    fun covers(day: DayOfWeek): Boolean = when (days) {
        VanTabDays.MON_THU -> day in DayOfWeek.MONDAY..DayOfWeek.THURSDAY
        VanTabDays.FRIDAY -> day == DayOfWeek.FRIDAY
    }

    companion object {
        /** The tab a title names, or null for a title the app doesn't know. */
        fun parse(title: String): VanTabKind? {
            val text = title.lowercase()
            val week = when {
                "current" in text -> VanWeek.CURRENT
                "next" in text -> VanWeek.NEXT
                else -> return null
            }
            val days = when {
                "fri" in text -> VanTabDays.FRIDAY
                Regex("""m\s*-\s*th|mon""").containsMatchIn(text) -> VanTabDays.MON_THU
                else -> return null
            }
            return VanTabKind(week, days)
        }
    }
}

/**
 * One tab as read: its [sites], or null when the tab is there but unreadable (AirCare's "An unexpected error
 * occurred." in it, which also makes the page answer HTTP 500); [problem] says why, for the log.
 */
data class VanTab(val kind: VanTabKind, val sites: List<VanSite>?, val problem: String? = null)

/** The van page as last read: its tabs, and when the app fetched it, which is all the age a live status has. */
data class VanSchedule(val tabs: List<VanTab>, val fetchedAt: Instant) {
    fun tab(kind: VanTabKind): VanTab? = tabs.firstOrNull { it.kind == kind }
}

/** How the last attempt to fetch the van page went, and whether there is a schedule to show. */
data class VanTimes(
    /** The last good schedule, or null before the first. */
    val schedule: VanSchedule? = null,
    val lastOutcome: FetchOutcome = FetchOutcome.OK,
)

/** Van rules that are numbers. */
object VanRules {
    /** A live status older than this, counted from the fetch, shows as "Scheduled". */
    val STATUS_STALE_AFTER: Duration = Duration.ofMinutes(30)

    /** How often to fetch the van page while the vans list is on screen. */
    val REFRESH_INTERVAL: Duration = Duration.ofMinutes(15)

    /** Farther than this by road, the nearest van is out of the way, and the list says so. */
    const val FAR_MINUTES = 20

    /** A launch fetch this soon after another is the same one. */
    val LAUNCH_GAP: Duration = Duration.ofMinutes(1)

    /** Units read cars on weekdays between these times; the page is fetched only then (apart from launch). */
    val COLLECTION_STARTS: LocalTime = LocalTime.of(5, 30)
    val COLLECTION_ENDS: LocalTime = LocalTime.of(19, 0)

    /** Weekdays, 5:30 AM to 7 PM in Denver. */
    fun collecting(at: Instant): Boolean {
        val local = at.atZone(AirCareHours.zone)
        val weekday = local.dayOfWeek != DayOfWeek.SATURDAY && local.dayOfWeek != DayOfWeek.SUNDAY
        val time = local.toLocalTime()
        return weekday && time >= COLLECTION_STARTS && time < COLLECTION_ENDS
    }
}

/**
 * Whether to fetch the van page now, for [reason], given when it was [lastFetch]ed: at launch and when the vans
 * tab opens ([FetchReason.LAUNCH]) whatever the time, unless it was fetched under [VanRules.LAUNCH_GAP] ago (the
 * launch and the tab opening at once); a tap on refresh while units can be out; otherwise every
 * [VanRules.REFRESH_INTERVAL] while units can be out ([VanRules.collecting]).
 */
fun vanWorthFetching(reason: FetchReason, now: Instant, lastFetch: Instant?): Boolean = when (reason) {
    FetchReason.LAUNCH -> lastFetch == null || Duration.between(lastFetch, now) >= VanRules.LAUNCH_GAP
    FetchReason.MANUAL -> VanRules.collecting(now)
    FetchReason.DUE -> VanRules.collecting(now) &&
        (lastFetch == null || Duration.between(lastFetch, now) >= VanRules.REFRESH_INTERVAL)
}

/**
 * The Monday of the week the page's "Current Week" tabs are for, on [today]: this week's on a weekday, and, until
 * it's known when AirCare rolls the tabs over, the week just ended on a Saturday or Sunday.
 */
fun currentVanWeek(today: LocalDate): LocalDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/** The date of [day] in [week], with [today] for "current". */
fun dateIn(week: VanWeek, day: DayOfWeek, today: LocalDate): LocalDate {
    val monday = currentVanWeek(today).plusWeeks(if (week == VanWeek.NEXT) 1 else 0)
    return monday.plusDays((day.value - DayOfWeek.MONDAY.value).toLong())
}

/** What a site is doing on a day, for its badge and status word. */
sealed interface VanStatus {
    /** "Reading cars now". */
    data object Reading : VanStatus

    /** "Set up, not reading". */
    data object Paused : VanStatus

    /** "On site, status unknown". */
    data object OnSiteUnknown : VanStatus

    /** "Not set up yet": due now, not there yet. */
    data object NotSetUpYet : VanStatus

    /** "Done for today": past its hours, or AirCare says it's no longer scheduled (weather can close a site early). */
    data object Done : VanStatus

    /** "Starts at 6:35 AM": today, before its hours. */
    data class StartsAt(val time: LocalTime) : VanStatus

    /** "Scheduled": another day, or today with no live status fresh enough to go by. */
    data object Scheduled : VanStatus
}

/**
 * [site]'s status on [date] at [now]. Live status counts only for today, and only while the fetch it came from is
 * no more than [VanRules.STATUS_STALE_AFTER] old; [liveUsable] is false when the page couldn't be read, so every
 * site shows as scheduled. A day other than today is [VanStatus.Scheduled].
 */
fun vanStatus(site: VanSite, date: LocalDate, now: Instant, fetchedAt: Instant, liveUsable: Boolean = true): VanStatus {
    val local = now.atZone(AirCareHours.zone)
    if (date != local.toLocalDate()) return VanStatus.Scheduled
    val time = local.toLocalTime()
    if (time >= site.closes) return VanStatus.Done
    val fresh = liveUsable && Duration.between(fetchedAt, now) <= VanRules.STATUS_STALE_AFTER
    val before = time < site.opens
    return when (if (fresh) site.status else null) {
        VanLiveStatus.TESTING -> VanStatus.Reading
        VanLiveStatus.NOT_TESTING -> VanStatus.Paused
        VanLiveStatus.UNKNOWN -> VanStatus.OnSiteUnknown
        VanLiveStatus.NOT_ON_SITE_SCHEDULED -> if (before) VanStatus.StartsAt(site.opens) else VanStatus.NotSetUpYet
        VanLiveStatus.NOT_ON_SITE_UNSCHEDULED -> if (before) VanStatus.StartsAt(site.opens) else VanStatus.Done
        null -> if (before) VanStatus.StartsAt(site.opens) else VanStatus.Scheduled
    }
}

/** One site on one day, as the list shows it. */
data class VanRow(
    val site: VanSite,
    val date: LocalDate,
    val status: VanStatus,
    /** The place it's in, from the bundled ZIP table by its coordinates ([ZipTable.placeOf]); AirCare's city if none. */
    val place: String,
    /** Routed drive minutes from "here" (the van drive table), or null without a location or a time for the site. */
    val driveMinutes: Int?,
)

/** What a day shows. */
sealed interface VanDayContent {
    /** The day's sites, nearest first. */
    data class Sites(val rows: List<VanRow>) : VanDayContent

    /** The day's tab is there, with no sites on it that day (Thanksgiving). [holiday] if it's one. */
    data class NoSites(val holiday: Holiday?) : VanDayContent

    /** The tab for the day is missing or unreadable: not posted yet. */
    data object NotPosted : VanDayContent
}

/** A day chip and what it shows. */
data class VanDay(val date: LocalDate, val isToday: Boolean, val content: VanDayContent)

/** The vans list's states, page-wide. A day's own state is its [VanDayContent]. */
enum class VanListState {
    /** A weekday, the page read: today's statuses are live (or "Scheduled" once stale). */
    LIVE,

    /** Saturday or Sunday: no units out; next week's days are shown. */
    WEEKEND,

    /** The last fetch failed and the statuses are too old to go by; the schedule still shows. */
    STALE,

    /** The page couldn't be read: the last good schedule, every status "Scheduled". */
    BROKEN,

    /** No schedule yet: the first fetch hasn't finished, or failed. */
    EMPTY,
}

/** The vans list at [now]. */
data class VanList(val state: VanListState, val days: List<VanDay>, val fetchedAt: Instant?)

fun vanListState(times: VanTimes, now: Instant): VanListState {
    val schedule = times.schedule
    val day = now.atZone(AirCareHours.zone).dayOfWeek
    return when {
        times.lastOutcome == FetchOutcome.UNREADABLE && schedule != null -> VanListState.BROKEN
        schedule == null -> VanListState.EMPTY
        day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY -> VanListState.WEEKEND
        times.lastOutcome != FetchOutcome.OK &&
            Duration.between(schedule.fetchedAt, now) > VanRules.STATUS_STALE_AFTER -> VanListState.STALE
        else -> VanListState.LIVE
    }
}

/**
 * The vans list at [now]: a chip per weekday from today (next Monday on a weekend) to the end of next week, each
 * with its sites nearest first by drive minutes from [from] ([drives]), sites without a time after them by name;
 * never by straight-line distance. [places] names where a site is.
 */
fun vanList(
    times: VanTimes,
    now: Instant,
    from: GeoPoint?,
    drives: (VanSite, GeoPoint) -> Int?,
    places: (GeoPoint) -> String?,
): VanList {
    val state = vanListState(times, now)
    val schedule = times.schedule ?: return VanList(state, emptyList(), null)
    val today = now.atZone(AirCareHours.zone).toLocalDate()
    val lastDay = dateIn(VanWeek.NEXT, DayOfWeek.FRIDAY, today)
    val firstDay = if (state == VanListState.WEEKEND) dateIn(VanWeek.NEXT, DayOfWeek.MONDAY, today) else today
    val days = generateSequence(firstDay) { it.plusDays(1) }
        .takeWhile { !it.isAfter(lastDay) }
        .filter { it.dayOfWeek != DayOfWeek.SATURDAY && it.dayOfWeek != DayOfWeek.SUNDAY }
        .map { date ->
            VanDay(date, date == today, dayContent(schedule, date, today, now, state != VanListState.BROKEN, from, drives, places))
        }
        .toList()
    return VanList(state, days, schedule.fetchedAt)
}

private fun dayContent(
    schedule: VanSchedule,
    date: LocalDate,
    today: LocalDate,
    now: Instant,
    liveUsable: Boolean,
    from: GeoPoint?,
    drives: (VanSite, GeoPoint) -> Int?,
    places: (GeoPoint) -> String?,
): VanDayContent {
    val week = if (currentVanWeek(date) == currentVanWeek(today)) VanWeek.CURRENT else VanWeek.NEXT
    val days = if (date.dayOfWeek == DayOfWeek.FRIDAY) VanTabDays.FRIDAY else VanTabDays.MON_THU
    val sites = schedule.tab(VanTabKind(week, days))?.sites ?: return VanDayContent.NotPosted
    val rows = sites
        .filter { date.dayOfWeek in it.days }
        .map { site ->
            VanRow(
                site = site,
                date = date,
                status = vanStatus(site, date, now, schedule.fetchedAt, liveUsable),
                place = places(site.point) ?: site.city,
                driveMinutes = from?.let { drives(site, it) },
            )
        }
    if (rows.isEmpty()) return VanDayContent.NoSites(AirCareHours.holidayOn(date))
    return VanDayContent.Sites(
        rows.sortedWith(compareBy<VanRow, Int?>(nullsLast()) { it.driveMinutes }.thenBy { VanSite.normalise(it.site.description) }),
    )
}

/**
 * The hero, "Nearest van reading cars now": of [day]'s rows, the reading one with the least drive. None on another
 * day, without a drive time, or when none is reading.
 */
fun nearestReading(day: VanDay): VanRow? {
    if (!day.isToday) return null
    val rows = (day.content as? VanDayContent.Sites)?.rows ?: return null
    return rows.filter { it.status == VanStatus.Reading && it.driveMinutes != null }.minByOrNull { it.driveMinutes!! }
}

/**
 * The nearest of [day]'s sites by drive, in minutes, when it is over [VanRules.FAR_MINUTES]: the list then says a
 * station test may be quicker. Null when a site is nearer, when no site has a drive time, or the day has no sites.
 */
fun nearestFarAway(day: VanDay?): Int? {
    val rows = (day?.content as? VanDayContent.Sites)?.rows ?: return null
    val nearest = rows.mapNotNull { it.driveMinutes }.minOrNull() ?: return null
    return nearest.takeIf { it > VanRules.FAR_MINUTES }
}

/** How many of [day]'s sites are reading now: the bottom bar's badge and the "6 reading now" line. */
fun readingCount(day: VanDay?): Int =
    ((day?.content as? VanDayContent.Sites)?.rows ?: emptyList()).count { it.status == VanStatus.Reading }

/** One day a site is posted for, for its detail's "When it's here". */
data class VanVisit(val date: LocalDate, val opens: LocalTime, val closes: LocalTime)

/**
 * Every day on the page's tabs that the site with [key] is posted for, in date order, this week's past days too.
 * [nextWeekPosted] says whether next week's tabs could be read, so "Not scheduled" next week means it.
 */
data class VanSiteWeek(val visits: List<VanVisit>, val nextWeekPosted: Boolean)

fun vanSiteWeek(schedule: VanSchedule, key: String, now: Instant): VanSiteWeek {
    val today = now.atZone(AirCareHours.zone).toLocalDate()
    val visits = schedule.tabs.flatMap { tab ->
        (tab.sites ?: emptyList()).filter { it.key == key }.flatMap { site ->
            site.days.filter(tab.kind::covers).map { VanVisit(dateIn(tab.kind.week, it, today), site.opens, site.closes) }
        }
    }.distinct().sortedBy { it.date }
    val nextWeekPosted = schedule.tabs.any { it.kind.week == VanWeek.NEXT && it.sites != null }
    return VanSiteWeek(visits, nextWeekPosted)
}

/** When [time] on [date] is, in Denver. */
fun denverTime(date: LocalDate, time: LocalTime): ZonedDateTime = date.atTime(time).atZone(AirCareHours.zone)
