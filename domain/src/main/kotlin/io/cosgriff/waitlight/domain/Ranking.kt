package io.cosgriff.waitlight.domain

import java.time.Instant

/**
 * One station on the list: its fixed facts, its last reading, and how far it is from the user. [reading] and
 * [updatedAt] are null until the app has read a wait for it.
 */
data class StationRow(
    val station: Station,
    val reading: WaitReading?,
    /** AirCare's "Last updated" for this station. */
    val updatedAt: Instant?,
    /** Straight-line miles from the user, or null when the app has no location. */
    val distanceMiles: Double?,
) {
    /** Estimated drive minutes ([DriveEstimate]), or null without a location. */
    val driveMinutes: Int? get() = distanceMiles?.let(DriveEstimate::minutes)

    /** Drive plus wait, with any wait counted as at most [WAIT_CAP_MINUTES]. Null without an estimate or a location. */
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

    /** The badge at [now]; see [badgeFor]. Closed outside hours, [Badge.NoReading] before the first reading. */
    fun badge(now: Instant, thresholds: WaitThresholds, dataUsable: Boolean = true): Badge {
        if (!AirCareHours.isOpen(now)) return Badge.Closed
        if (reading == null || updatedAt == null) return Badge.NoReading
        return badgeFor(reading, updatedAt, now, thresholds, dataUsable)
    }

    companion object {
        const val WAIT_CAP_MINUTES = 90
    }
}

/** The order the user picked with the chips. */
enum class SortOrder { NEAREST, FASTEST_OVERALL }

/**
 * A row for every station, with its reading if there is one, and the straight-line distance from [from]. Null
 * [from] (no location) gives no distances.
 */
fun rowsFor(
    readings: Map<Int, Pair<WaitReading, Instant>>,
    from: GeoPoint?,
): List<StationRow> = Stations.all.map { station ->
    val latest = readings[station.id]
    StationRow(station, latest?.first, latest?.second, from?.let { station.location.milesTo(it) })
}

/**
 * The list as shown. Without a location, alphabetical whatever the chip says; while closed, nearest first, since
 * there are no waits to add. Fastest overall puts stations with an estimate first, by drive plus wait, then the
 * rest by distance; ties go to the nearer one. [lowProfileRimsOnly] keeps only the stations that can test them.
 */
fun arrange(
    rows: List<StationRow>,
    order: SortOrder,
    lowProfileRimsOnly: Boolean,
    state: ListState,
): List<StationRow> {
    val shown = if (lowProfileRimsOnly) rows.filter { it.station.lowProfileRims } else rows
    if (shown.any { it.distanceMiles == null }) return shown.sortedBy { it.station.name }
    val nearest = compareBy<StationRow> { it.distanceMiles }
    return when {
        order == SortOrder.FASTEST_OVERALL && state != ListState.CLOSED ->
            shown.sortedWith(compareBy<StationRow, Int?>(nullsLast()) { it.totalMinutes }.then(nearest))
        else -> shown.sortedWith(nearest)
    }
}

/**
 * The "Fastest overall right now" card: the station with the least drive plus wait among [rows] (already
 * filtered by the chips). None while closed, while the data is stale, offline or broken, without a location, or
 * when no station has a fresh estimate.
 */
fun fastestOverall(rows: List<StationRow>, state: ListState, now: Instant): StationRow? {
    if (state != ListState.LIVE && state != ListState.DUE) return null
    return rows
        .filter { row -> row.totalMinutes != null && row.updatedAt?.let { Freshness.isFresh(it, now) } == true }
        .minWithOrNull(compareBy<StationRow> { it.totalMinutes }.thenBy { it.distanceMiles })
}

/** The newest "Last updated" among [rows]: how fresh the data as a whole is. */
fun newestUpdate(rows: Collection<StationRow>): Instant? = rows.mapNotNull { it.updatedAt }.maxOrNull()
