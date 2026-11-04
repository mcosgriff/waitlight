package io.cosgriff.waitlight.domain

import java.time.Instant

/** The waits the app knows and how the last fetch went. */
data class WaitTimes(
    /**
     * Each station's last good reading and AirCare's "Last updated" time for it, by `locationID`. Kept through
     * failed fetches and unreadable pages, so the badges can show it as stale.
     */
    val waits: Map<Int, Pair<WaitReading, Instant>> = emptyMap(),
    val lastOutcome: FetchOutcome = FetchOutcome.OK,
) {
    /** False after an unreadable page: every badge shows its last good reading as stale. */
    val dataUsable: Boolean get() = lastOutcome != FetchOutcome.UNREADABLE

    /** The data's age goes by this, never the fetch time. */
    val newestUpdate: Instant? get() = waits.values.maxOfOrNull { it.second }
}
