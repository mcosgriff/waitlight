package io.cosgriff.waitlight.domain

import java.time.Instant

/**
 * One station at a moment, as the list, the detail and the widget show it: its fixed facts, its last reading, the
 * routed drive to it from the user, and its badge then. [reading] and [updatedAt] are null until the app has read
 * a wait for it. Made by [rowsFor]. There is no straight-line distance: drive minutes are the only measure shown
 * or sorted by (ADR-0007).
 */
data class StationRow(
    val station: Station,
    val reading: WaitReading?,
    /** AirCare's "Last updated" for this station. */
    val updatedAt: Instant?,
    /** The app knows where the user is ("here"), so the list can go by drive time rather than by name. */
    val located: Boolean,
    /**
     * Routed drive minutes from the user ([DriveTable]), or null without a location or where the table has none
     * (outside the gas emissions program area).
     */
    val driveMinutes: Int?,
    /** The badge at the row's moment ([badgeFor]): closed outside hours, [Badge.NoReading] before the first reading. */
    val badge: Badge,
) {
    /**
     * Drive plus wait, with any wait counted as at most [WAIT_CAP_MINUTES]. Null without a wait estimate or a drive
     * time.
     */
    val totalMinutes: Int?
        get() {
            val drive = driveMinutes ?: return null
            val wait = when (reading) {
                is WaitReading.Minutes -> reading.minutes.coerceAtMost(WAIT_CAP_MINUTES)
                // "More than 60" has no top; it counts as the cap.
                is WaitReading.MoreThan -> WAIT_CAP_MINUTES
                WaitReading.NoEstimate, null -> return null
            }
            return drive + wait
        }

    companion object {
        const val WAIT_CAP_MINUTES = 90
    }
}

/** The order the user picked with the chips. */
enum class SortOrder { NEAREST, FASTEST_OVERALL }

/**
 * Every station at [now]: its reading in [times] if there is one, the drive time from [from] in [drives], and its
 * badge ([WaitThresholds.STANDARD]). Each badge goes stale on its own station's time; all of them after an
 * unreadable page. Null [from] (no location) gives no drive.
 */
fun rowsFor(times: WaitTimes, from: GeoPoint?, drives: DriveTable, now: Instant): List<StationRow> =
    Stations.all.map { station ->
        val latest = times.waits[station.id]
        StationRow(
            station,
            latest?.first,
            latest?.second,
            located = from != null,
            driveMinutes = from?.let { drives.minutes(it, station.id) },
            badge = badgeAt(latest, now, times.dataUsable),
        )
    }

private fun badgeAt(latest: Pair<WaitReading, Instant>?, now: Instant, dataUsable: Boolean): Badge = when {
    !AirCareHours.isOpen(now) -> Badge.Closed
    latest == null -> Badge.NoReading
    else -> badgeFor(latest.first, latest.second, now, WaitThresholds.STANDARD, dataUsable)
}

/**
 * The list as shown. Without a location, alphabetical whatever the chip says; while closed, nearest first, since
 * there are no waits to add. Nearest is the shortest drive; stations with no drive time (outside the routed area)
 * go last, by name, never by straight-line distance. Fastest overall puts stations with an estimate first, by
 * drive plus wait, then the rest by drive; ties go to the shorter drive. [lowProfileRimsOnly] keeps only the
 * stations that can test them.
 */
fun arrange(
    rows: List<StationRow>,
    order: SortOrder,
    lowProfileRimsOnly: Boolean,
    state: ListState,
): List<StationRow> {
    val shown = if (lowProfileRimsOnly) rows.filter { it.station.lowProfileRims } else rows
    if (shown.any { !it.located }) return shown.sortedBy { it.station.name }
    val nearest = compareBy<StationRow, Int?>(nullsLast()) { it.driveMinutes }.thenBy { it.station.name }
    return when {
        order == SortOrder.FASTEST_OVERALL && state != ListState.CLOSED ->
            shown.sortedWith(compareBy<StationRow, Int?>(nullsLast()) { it.totalMinutes }.then(nearest))
        else -> shown.sortedWith(nearest)
    }
}

/**
 * The "Fastest overall right now" card: the station with the least drive plus wait among [rows] (already
 * filtered by the chips). None while closed, while the data is stale, offline or broken, without a location, or
 * when no station has a fresh estimate, which is a live badge ([Badge.Live]).
 */
fun fastestOverall(rows: List<StationRow>, state: ListState): StationRow? {
    if (state != ListState.LIVE && state != ListState.DUE) return null
    return rows
        .filter { row -> row.totalMinutes != null && row.badge is Badge.Live }
        .minWithOrNull(compareBy<StationRow> { it.totalMinutes }.thenBy { it.driveMinutes })
}
