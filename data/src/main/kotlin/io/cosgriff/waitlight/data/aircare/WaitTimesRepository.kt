package io.cosgriff.waitlight.data.aircare

import android.util.Log
import io.cosgriff.waitlight.data.di.ApplicationScope
import io.cosgriff.waitlight.domain.FetchOutcome
import io.cosgriff.waitlight.domain.FetchReason
import io.cosgriff.waitlight.domain.WaitTimes
import io.cosgriff.waitlight.domain.worthFetching
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.UnknownHostException
import java.time.Clock
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides whether to fetch AirCare's page ([worthFetching]), fetches and reads it, and keeps the latest
 * [WaitTimes], on disk too ([WaitTimesStore]), so they survive a restart. The stored waits load in [scope] as soon
 * as the repository starts; until then [waitTimes] is empty, and a [refresh] waits for them.
 */
@Singleton
class WaitTimesRepository @Inject constructor(
    private val client: AirCareClient,
    private val clock: Clock,
    private val store: WaitTimesStore,
    @param:ApplicationScope scope: CoroutineScope,
) {
    private val state = MutableStateFlow(WaitTimes())
    private val busy = MutableStateFlow(false)
    private val oneFetchAtATime = Mutex()

    /** Guards [unfinished] and the writes to [busy], so a refresh finishing can't clear one that's starting. */
    private val count = Any()

    /** Refreshes asked for and not yet finished, queued or running. */
    private var unfinished = 0

    val waitTimes: StateFlow<WaitTimes> = state.asStateFlow()

    /**
     * True from the moment a [refresh] starts fetching until the last refresh asked for meanwhile has finished,
     * so overlapping refreshes show as one. A refresh that decides not to fetch never sets it.
     */
    val fetching: StateFlow<Boolean> = busy.asStateFlow()

    private val loaded = scope.async {
        store.load()?.let { stored -> state.compareAndSet(WaitTimes(), stored) }
    }

    /** The waits once the stored ones are loaded: for the widget, which may be drawn in a process just started. */
    suspend fun current(): WaitTimes {
        loaded.await()
        return state.value
    }

    /**
     * Fetches the page if it's worth it for [reason] ([worthFetching]): when AirCare's next update is due, any
     * time the stations are open for a tap on refresh, and always at launch. Calls made while a fetch is running
     * wait for it, then find the data fresh.
     */
    suspend fun refresh(reason: FetchReason) {
        synchronized(count) { unfinished++ }
        try {
            oneFetchAtATime.withLock {
                loaded.await()
                if (!worthFetching(reason, clock.instant(), state.value.newestUpdate)) return
                synchronized(count) { busy.value = true }
                val before = state.value
                fetch(reason)
                if (state.value != before) store.save(state.value)
            }
        } finally {
            synchronized(count) { if (--unfinished == 0) busy.value = false }
        }
    }

    /** A new list's fetches: one per list on screen, so each list makes its own launch fetch. */
    fun forList(): ListFetches = ListFetches(this)

    private suspend fun fetch(reason: FetchReason) {
        val html = try {
            client.page()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // No network shows up as a failed DNS lookup, wrapped or not; anything else is AirCare's end.
            val outcome = if (e.causes().any { it is UnknownHostException }) FetchOutcome.OFFLINE else FetchOutcome.FAILED
            Log.i(TAG, "Fetch $outcome", e)
            state.value = state.value.copy(lastOutcome = outcome)
            return
        }

        state.value = when (val read = AirCarePage.read(html)) {
            is PageRead.Read -> {
                if (!read.hoursAsStated) Log.w(TAG, "AirCare's stated hours changed; check AirCareHours")
                Log.i(TAG, "Fetched waits for ${read.waits.size} stations ($reason)")
                // A station that says it's closed has no reading; it keeps its last good one.
                WaitTimes(state.value.waits + read.waits, FetchOutcome.OK)
            }
            is PageRead.Unreadable -> {
                Log.w(TAG, "AirCare's page couldn't be read: ${read.why}")
                state.value.copy(lastOutcome = FetchOutcome.UNREADABLE)
            }
        }
    }

    private fun Throwable.causes() = generateSequence(this) { it.cause }

    private companion object {
        const val TAG = "Waitlight"
    }
}

/**
 * One list's ticks. The first is its launch fetch ([FetchReason.LAUNCH]), whatever the time; the rest, also after
 * the screen comes back, fetch only when AirCare's next update is due ([FetchReason.DUE]).
 */
class ListFetches internal constructor(private val repository: WaitTimesRepository) {
    private val launched = AtomicBoolean(false)

    suspend fun tick() = repository.refresh(if (launched.getAndSet(true)) FetchReason.DUE else FetchReason.LAUNCH)
}
