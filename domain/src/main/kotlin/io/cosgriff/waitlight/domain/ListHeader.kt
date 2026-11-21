package io.cosgriff.waitlight.domain

import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime

/** Where "here" is, as the list's header names it. `:app` words it. */
sealed interface PlaceLine {
    /** A typed ZIP code's place, "Arvada, CO 80003": a link to change or clear it. */
    data class Zip(val place: ZipPlace) : PlaceLine

    /** The phone's fix, in [place]. */
    data class Device(val place: ZipPlace) : PlaceLine

    /** The phone's fix, near no Colorado ZIP: "Near you". */
    data object NearYou : PlaceLine

    /** Location allowed, no fix yet: "Finding your location…". */
    data object Finding : PlaceLine

    /** Neither location nor a ZIP: "Location off", a link to the prompt. */
    data object LocationOff : PlaceLine
}

/** The line beside the place: how old the waits are. */
sealed interface UpdatedLine {
    /** Outside hours: "Closed since 5:30 PM". */
    data class ClosedSince(val at: ZonedDateTime) : UpdatedLine

    /** No waits yet, the first fetch due: "Getting wait times…". */
    data object Getting : UpdatedLine

    /** No waits yet: "No wait times yet". */
    data object NoneYet : UpdatedLine

    /** Live or due: "Updated just now" (0) or "Updated N min ago". */
    data class MinutesAgo(val minutes: Int) : UpdatedLine

    /** Stale, offline or broken: "Last updated 2:14 PM". */
    data class LastUpdated(val at: Instant) : UpdatedLine
}

/** The line under the header, or none. */
sealed interface NextLine {
    /** Closed: "Waits return when stations open". */
    data object WaitsReturn : NextLine

    /** Live: "Next update in ~N min". */
    data class NextUpdateIn(val minutes: Int) : NextLine

    /** Due with data: "New data is ready — tap refresh", in the primary colour. */
    data object NewData : NextLine

    /** Stale or offline: how old the newest data is, in the warning colour. */
    data class Age(val age: Duration) : NextLine

    /** Broken: "Waits unavailable", in the error colour. */
    data object Unavailable : NextLine
}

/** The banner under the header. */
enum class ListBanner { STALE, OFFLINE, BROKEN }

/**
 * The sort chips: shown only with a location (without one the list is alphabetical whatever they say), and while
 * closed "Nearest" is the one selected, since [arrange] goes by nearest then.
 */
data class SortChips(val shown: Boolean, val nearestSelected: Boolean, val fastestSelected: Boolean)

/** When the stations open next, and on which day, for the closed card and each row while closed. */
data class Opening(val at: ZonedDateTime, val day: OpeningDay)

/** Everything the list's header, banner and chips decide, typed; `:app` only words it. */
data class ListHeader(
    val place: PlaceLine,
    val updated: UpdatedLine,
    val next: NextLine?,
    val banner: ListBanner?,
    val chips: SortChips,
    /** While closed, when the stations open next; null while open. */
    val opening: Opening?,
)

/** The header for the list in [state] at [now]. */
fun listHeader(
    state: ListState,
    now: Instant,
    newestUpdate: Instant?,
    refresh: RefreshButton,
    here: Here?,
    locationAllowed: Boolean,
    order: SortOrder,
): ListHeader {
    val closed = state == ListState.CLOSED
    return ListHeader(
        place = placeLine(here, locationAllowed),
        updated = when {
            closed -> UpdatedLine.ClosedSince(AirCareHours.lastClosing(now))
            newestUpdate == null && state == ListState.DUE -> UpdatedLine.Getting
            newestUpdate == null -> UpdatedLine.NoneYet
            state == ListState.LIVE || state == ListState.DUE ->
                UpdatedLine.MinutesAgo(Duration.between(newestUpdate, now).toMinutes().coerceAtLeast(0).toInt())
            else -> UpdatedLine.LastUpdated(newestUpdate)
        },
        next = when (state) {
            ListState.CLOSED -> NextLine.WaitsReturn
            ListState.LIVE -> refresh.nextUpdateInMinutes?.let { NextLine.NextUpdateIn(it) }
            ListState.DUE -> if (newestUpdate == null) null else NextLine.NewData
            ListState.STALE, ListState.OFFLINE -> newestUpdate?.let { NextLine.Age(Duration.between(it, now)) }
            ListState.BROKEN -> NextLine.Unavailable
        },
        banner = when (state) {
            ListState.STALE -> ListBanner.STALE
            ListState.OFFLINE -> ListBanner.OFFLINE
            ListState.BROKEN -> ListBanner.BROKEN
            else -> null
        },
        chips = SortChips(
            shown = here != null,
            nearestSelected = order == SortOrder.NEAREST || closed,
            fastestSelected = order == SortOrder.FASTEST_OVERALL && !closed,
        ),
        opening = if (closed) AirCareHours.nextOpening(now).let { Opening(it, openingDay(now, it)) } else null,
    )
}

/** Where "here" is, as the header names it ([here], [ZipTable.placeOf]). */
fun placeLine(here: Here?, locationAllowed: Boolean): PlaceLine = when {
    here is Here.Zip -> PlaceLine.Zip(here.place)
    here != null -> here.place?.let { PlaceLine.Device(it) } ?: PlaceLine.NearYou
    locationAllowed -> PlaceLine.Finding
    else -> PlaceLine.LocationOff
}
