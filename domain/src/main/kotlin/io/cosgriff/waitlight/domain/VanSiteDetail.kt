package io.cosgriff.waitlight.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * A van site's detail on one day at a moment, as its screen shows it ([vanSiteDetail]). The screen only words and
 * draws it: which hours, whether "Status checked" shows, the "When it's here" lines and the sketch's pin are decided
 * here.
 */
data class VanSiteDetail(
    val row: VanRow,
    /** The list's state, for the row's status words. */
    val state: VanListState,
    /** Whether [row] is today's. */
    val isToday: Boolean,
    val hours: VanDetailHours,
    /** When the page was fetched, for "Status checked …"; only today, else null. */
    val checkedAt: Instant?,
    /** "When it's here": every day the site is posted for, in date order, then next week's "Not scheduled" if so. */
    val week: List<VanWeekLine>,
    /** The unit's pin in the sketch. */
    val pin: VanPin,
    /** The user's drive-bys past this site, newest first. */
    val driveBys: List<DriveBy>,
)

/** The hours beside the place. */
sealed interface VanDetailHours {
    /** "until 5 PM": today, and not done for the day. */
    data class Until(val closes: LocalTime) : VanDetailHours

    /** "7 AM – 5 PM": another day, or done for today. */
    data class Range(val opens: LocalTime, val closes: LocalTime) : VanDetailHours
}

/** A line of "When it's here". */
sealed interface VanWeekLine {
    /** A day the site is posted for: today in bold, past days muted. */
    data class Visit(val date: LocalDate, val opens: LocalTime, val closes: LocalTime, val isToday: Boolean, val isPast: Boolean) : VanWeekLine

    /** "Next week: Not scheduled": next week's tabs were read and the site isn't on them. */
    data object NextWeekNotScheduled : VanWeekLine
}

/**
 * The colour of the unit's pin in the detail's sketch, by its status: reading, set up and not reading, or anything
 * else. The status is in words beside its badge too, so colour is never the only signal.
 */
enum class VanPin { READING, SET_UP, OTHER }

/**
 * The detail of the site with [key] on [date] at [now], from the vans [list] at [now], the [schedule] it was made
 * from (for the site's week, [vanSiteWeek]) and the user's drive-by [log]. Null when the site isn't on [list] for
 * [date] (it has left the page, or the day has gone).
 */
fun vanSiteDetail(list: VanList, schedule: VanSchedule?, key: String, date: LocalDate, now: Instant, log: DriveByLog): VanSiteDetail? {
    if (schedule == null) return null
    val day = list.days.firstOrNull { it.date == date } ?: return null
    val row = (day.content as? VanDayContent.Sites)?.rows?.firstOrNull { it.site.key == key } ?: return null
    val isToday = day.isToday
    val hours = if (isToday && row.status != VanStatus.Done) {
        VanDetailHours.Until(row.site.closes)
    } else {
        VanDetailHours.Range(row.site.opens, row.site.closes)
    }
    val pin = when (row.status) {
        VanStatus.Reading -> VanPin.READING
        VanStatus.Paused -> VanPin.SET_UP
        else -> VanPin.OTHER
    }
    return VanSiteDetail(
        row = row,
        state = list.state,
        isToday = isToday,
        hours = hours,
        checkedAt = if (isToday) list.fetchedAt else null,
        week = weekLines(vanSiteWeek(schedule, key, now), now.atZone(AirCareHours.zone).toLocalDate()),
        pin = pin,
        driveBys = log.at(key),
    )
}

private fun weekLines(week: VanSiteWeek, today: LocalDate): List<VanWeekLine> {
    val visits = week.visits.map { VanWeekLine.Visit(it.date, it.opens, it.closes, isToday = it.date == today, isPast = it.date < today) }
    val nextMonday = dateIn(VanWeek.NEXT, DayOfWeek.MONDAY, today)
    val notNextWeek = week.nextWeekPosted && week.visits.none { it.date >= nextMonday }
    return if (notNextWeek) visits + VanWeekLine.NextWeekNotScheduled else visits
}
