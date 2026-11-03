package io.cosgriff.waitlight.data.aircare

import android.util.Log
import io.cosgriff.waitlight.domain.AirCareHours
import io.cosgriff.waitlight.domain.FetchOutcome
import io.cosgriff.waitlight.domain.WaitReading
import io.cosgriff.waitlight.domain.shouldFetch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.UnknownHostException
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

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

/** Fetches and reads AirCare's page, only during opening hours, and keeps the latest [WaitTimes]. */
@Singleton
class WaitTimesRepository @Inject constructor(
    private val client: AirCareClient,
    private val clock: Clock,
) {
    private val state = MutableStateFlow(WaitTimes())
    private val fetching = Mutex()

    val waitTimes: StateFlow<WaitTimes> = state.asStateFlow()

    /**
     * Fetches the page if it's worth it: while open and the data is due ([shouldFetch]), or, for a tap on the
     * refresh button ([manual]), any time the stations are open. Never outside opening hours. Returns whether it
     * fetched. Calls made while a fetch is running wait for it, then find the data fresh.
     */
    suspend fun refresh(manual: Boolean = false): Boolean = fetching.withLock {
        val now = clock.instant()
        val worthIt = if (manual) AirCareHours.isOpen(now) else shouldFetch(now, state.value.newestUpdate)
        if (!worthIt) return false

        val html = try {
            client.page()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // No network shows up as a failed DNS lookup, wrapped or not; anything else is AirCare's end.
            val outcome = if (e.causes().any { it is UnknownHostException }) FetchOutcome.OFFLINE else FetchOutcome.FAILED
            Log.i(TAG, "Fetch $outcome", e)
            state.value = state.value.copy(lastOutcome = outcome)
            return true
        }

        state.value = when (val read = AirCarePage.read(html)) {
            is PageRead.Read -> {
                if (!read.hoursAsStated) Log.w(TAG, "AirCare's stated hours changed; check AirCareHours")
                WaitTimes(read.waits, FetchOutcome.OK)
            }
            is PageRead.Unreadable -> {
                Log.w(TAG, "AirCare's page couldn't be read: ${read.why}")
                state.value.copy(lastOutcome = FetchOutcome.UNREADABLE)
            }
        }
        true
    }

    private fun Throwable.causes() = generateSequence(this) { it.cause }

    private companion object {
        const val TAG = "Waitlight"
    }
}
