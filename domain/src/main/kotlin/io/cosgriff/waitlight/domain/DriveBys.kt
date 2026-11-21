package io.cosgriff.waitlight.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * A time the user says they drove past a van site: their own record, kept on the phone. The app can't see whether
 * the unit read the car, or whether the reading was clean, so this is never a claim that a reading counted. The
 * site's description and point are kept with it, so an entry outlives the site leaving AirCare's page. [vehicleId]
 * is the [Vehicle] that drove past; null for entries logged before vehicles were, or with none saved.
 */
data class DriveBy(
    val id: Long,
    val siteKey: String,
    val description: String,
    val point: GeoPoint,
    val at: Instant,
    val vehicleId: Long? = null,
) {
    val name: VanName get() = VanName.of(description)
}

/** The user's drive-bys, newest first, and how many fall in the RapidScreen window. */
data class DriveByLog(val entries: List<DriveBy>, val inWindow: Int)

object DriveByRules {
    /** Two clean readings within this many months before renewal can skip a test (AirCare's RapidScreen rule). */
    const val WINDOW_MONTHS = 14L

    /** The first day still in the window on [today]: 14 months back, in Denver. */
    fun windowStart(today: LocalDate): LocalDate = today.minusMonths(WINDOW_MONTHS)
}

/** [entries] newest first, counting those on or after [DriveByRules.windowStart] of [now]'s day. */
fun driveByLog(entries: List<DriveBy>, now: Instant): DriveByLog {
    val start = DriveByRules.windowStart(now.atZone(AirCareHours.zone).toLocalDate()).atStartOfDay(AirCareHours.zone).toInstant()
    val sorted = entries.sortedWith(compareByDescending<DriveBy> { it.at }.thenByDescending { it.id })
    return DriveByLog(sorted, sorted.count { !it.at.isBefore(start) && !it.at.isAfter(now) })
}

/** When a drive-by on [date] at [time] in Denver was, or null if that's still to come at [now]: no logging ahead. */
fun driveByAt(date: LocalDate, time: LocalTime, now: Instant): Instant? =
    date.atTime(time).atZone(AirCareHours.zone).toInstant().takeUnless { it.isAfter(now) }

/** The drive-bys at the site with [key], newest first. */
fun DriveByLog.at(key: String): List<DriveBy> = entries.filter { it.siteKey == key }
