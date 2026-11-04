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

    /**
     * The [WIDGET_STATIONS] nearest stations, nearest first, each with its distance, and the newest AirCare time
     * of all of them.
     */
    data class Nearest(val rows: List<StationRow>, val newestUpdate: Instant?) : WidgetContent
}

/**
 * The widget at [now]: the nearest stations to [here], the same rows as the list's ([rowsFor]), or
 * [WidgetContent.NeedsLocation] without a location.
 */
fun widgetContent(times: WaitTimes, here: GeoPoint?, now: Instant): WidgetContent {
    if (here == null) return WidgetContent.NeedsLocation
    // The widget shows miles, not drive times.
    val all = rowsFor(times, here, DriveTable.EMPTY, now)
    val state = listState(now, times.newestUpdate, times.lastOutcome)
    val nearest = arrange(all, SortOrder.NEAREST, lowProfileRimsOnly = false, state = state).take(WIDGET_STATIONS)
    return WidgetContent.Nearest(nearest, times.newestUpdate)
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
