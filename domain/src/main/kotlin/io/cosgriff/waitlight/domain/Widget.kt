package io.cosgriff.waitlight.domain

import java.time.Instant

/** How many stations the home-screen widget shows. */
const val WIDGET_STATIONS = 3

/** What the home-screen widget shows. */
sealed interface WidgetContent {
    /**
     * No location, so no nearest stations: a line asking to open the app. Not the first stations alphabetically,
     * which would look like the nearest ones.
     */
    data object NeedsLocation : WidgetContent

    /** The [WIDGET_STATIONS] nearest stations, nearest first, and the newest AirCare time of all of them. */
    data class Nearest(val rows: List<WidgetRow>, val newestUpdate: Instant?) : WidgetContent
}

/** One station on the widget, with its badge at the time the widget was drawn. */
data class WidgetRow(
    val station: Station,
    val distanceMiles: Double,
    val badge: Badge,
    /** AirCare's "Last updated" for this station, or null before its first reading. */
    val updatedAt: Instant?,
)

/**
 * The widget at [now]: the nearest stations to [here] with the same badges as the list ([StationRow.badge]), or
 * [WidgetContent.NeedsLocation] without a location.
 */
fun widgetContent(
    readings: Map<Int, Pair<WaitReading, Instant>>,
    lastOutcome: FetchOutcome,
    here: GeoPoint?,
    now: Instant,
    thresholds: WaitThresholds,
): WidgetContent {
    if (here == null) return WidgetContent.NeedsLocation
    // The widget shows miles, not drive times.
    val all = rowsFor(readings, here, DriveTable.EMPTY)
    val newest = newestUpdate(all)
    val dataUsable = lastOutcome != FetchOutcome.UNREADABLE
    val nearest = arrange(all, SortOrder.NEAREST, lowProfileRimsOnly = false, state = listState(now, newest, lastOutcome))
        .take(WIDGET_STATIONS)
        .map { row -> WidgetRow(row.station, row.distanceMiles!!, row.badge(now, thresholds, dataUsable), row.updatedAt) }
    return WidgetContent.Nearest(nearest, newest)
}

/**
 * When the widget, drawn at [now], next needs drawing again though no new data has come: the moment the first
 * of its fresh badges goes stale, or closing time, whichever is first. Null while closed or without a location:
 * the background checks draw it again when the stations open.
 */
fun widgetRedrawAt(content: WidgetContent, now: Instant): Instant? {
    if (content !is WidgetContent.Nearest) return null
    val closing = AirCareHours.closingTime(now)?.toInstant() ?: return null
    // Fresh up to and including STALE_AFTER; a second later it is stale.
    val staleAt = content.rows
        .filter { it.badge is Badge.Live || it.badge == Badge.Open }
        .mapNotNull { row -> row.updatedAt?.plus(Freshness.STALE_AFTER)?.plusSeconds(1) }
        .filter { it > now }
    return (staleAt + closing).min()
}
