package io.cosgriff.waitlight.domain

import java.time.Duration
import java.time.Instant

/** How old AirCare's data may get. Ages come from AirCare's own "Last updated" times, never the fetch time. */
object Freshness {
    /** AirCare updates about this often. */
    val REFRESH_INTERVAL: Duration = Duration.ofMinutes(15)

    /** Older than this, a reading is greyed out as stale. */
    val STALE_AFTER: Duration = Duration.ofMinutes(30)

    fun isFresh(updatedAt: Instant, now: Instant): Boolean = Duration.between(updatedAt, now) <= STALE_AFTER
}

/** How the last attempt to fetch the page went. */
enum class FetchOutcome {
    /** Fetched and read. */
    OK,

    /** AirCare's site didn't answer, or answered with an error. */
    FAILED,

    /** The phone has no network. */
    OFFLINE,

    /** Fetched, but the page couldn't be read: AirCare changed its site. */
    UNREADABLE,
}

/** The list screen's states, as drawn in `design/png/State*`. */
enum class ListState {
    /** Fresh data, refresh not due yet. */
    LIVE,

    /** The newest data is [Freshness.REFRESH_INTERVAL] old: newer is probably waiting. */
    DUE,

    /** Outside opening hours. */
    CLOSED,

    /** The last fetch failed and what we have is older than [Freshness.STALE_AFTER]. */
    STALE,

    /** No network and what we have is older than [Freshness.STALE_AFTER]. */
    OFFLINE,

    /** The page couldn't be read; the last good data is shown greyed. */
    BROKEN,
}

/**
 * The list's state. [newestUpdate] is the newest "Last updated" time among the stations, null before the first
 * successful fetch. A failed fetch while the data is still fresh changes nothing on screen.
 */
fun listState(now: Instant, newestUpdate: Instant?, lastOutcome: FetchOutcome): ListState {
    if (!AirCareHours.isOpen(now)) return ListState.CLOSED
    if (lastOutcome == FetchOutcome.UNREADABLE) return ListState.BROKEN
    val fresh = newestUpdate != null && Freshness.isFresh(newestUpdate, now)
    if (!fresh) {
        when (lastOutcome) {
            FetchOutcome.FAILED -> return ListState.STALE
            FetchOutcome.OFFLINE -> return ListState.OFFLINE
            else -> Unit
        }
    }
    val age = newestUpdate?.let { Duration.between(it, now) }
    return if (age == null || age >= Freshness.REFRESH_INTERVAL) ListState.DUE else ListState.LIVE
}

/** The refresh button in the list's header. */
data class RefreshButton(
    val enabled: Boolean,
    /** Filled in the primary colour: new data is ready. */
    val emphasized: Boolean,
    /** "Next update in ~N min", while waiting for AirCare's next update. */
    val nextUpdateInMinutes: Int?,
)

fun refreshButton(state: ListState, now: Instant, newestUpdate: Instant?): RefreshButton = when (state) {
    ListState.CLOSED -> RefreshButton(enabled = false, emphasized = false, nextUpdateInMinutes = null)
    ListState.DUE -> RefreshButton(enabled = true, emphasized = true, nextUpdateInMinutes = null)
    ListState.LIVE -> {
        val due = newestUpdate!!.plus(Freshness.REFRESH_INTERVAL)
        // Rounded up, and never "0 min" while the button is still disabled.
        val seconds = Duration.between(now, due).seconds.coerceAtLeast(1)
        RefreshButton(enabled = false, emphasized = false, nextUpdateInMinutes = ((seconds + 59) / 60).toInt())
    }
    // Retry.
    ListState.STALE, ListState.OFFLINE, ListState.BROKEN ->
        RefreshButton(enabled = true, emphasized = false, nextUpdateInMinutes = null)
}

/**
 * Whether the app should fetch now. Never outside opening hours; otherwise when the newest data is at least a
 * refresh interval old, or there is none.
 */
fun shouldFetch(now: Instant, newestUpdate: Instant?): Boolean {
    if (!AirCareHours.isOpen(now)) return false
    return newestUpdate == null || Duration.between(newestUpdate, now) >= Freshness.REFRESH_INTERVAL
}
