package io.cosgriff.waitlight.data.vans

import android.util.Log
import io.cosgriff.waitlight.data.di.ApplicationScope
import io.cosgriff.waitlight.domain.FetchOutcome
import io.cosgriff.waitlight.domain.FetchReason
import io.cosgriff.waitlight.domain.VanSchedule
import io.cosgriff.waitlight.domain.VanTimes
import io.cosgriff.waitlight.domain.vanWorthFetching
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
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides whether to fetch AirCare's van page ([vanWorthFetching]), fetches it and each tab's table, reads them
 * ([VanPage]) and keeps the last good schedule, on disk too ([VanStore]). An unreadable page or a failed fetch
 * keeps the schedule it had and says so in [VanTimes.lastOutcome].
 */
@Singleton
class VanRepository @Inject constructor(
    private val client: VanClient,
    private val clock: Clock,
    private val store: VanStore,
    @param:ApplicationScope scope: CoroutineScope,
) {
    private val state = MutableStateFlow(VanTimes())
    private val busy = MutableStateFlow(false)
    private val oneFetchAtATime = Mutex()

    /** When the page was last asked for, read or not. */
    @Volatile private var lastAttempt: Instant? = null

    /** Guards the counts below and the writes to [busy], so a refresh finishing can't clear one that's starting. */
    private val count = Any()

    /** Refreshes asked for and not yet finished, queued or running. */
    private var unfinished = 0

    /** Fetches begun so far, and whether one is under way: what a launch asked meanwhile takes as its own. */
    private var begun = 0
    private var underWay = false

    val vans: StateFlow<VanTimes> = state.asStateFlow()

    /**
     * True from the moment a [refresh] starts fetching until the last refresh asked for meanwhile has finished,
     * so overlapping refreshes show as one. A refresh that decides not to fetch never sets it.
     */
    val fetching: StateFlow<Boolean> = busy.asStateFlow()

    private val loaded = scope.async {
        store.load()?.let { stored -> state.compareAndSet(VanTimes(), stored) }
    }

    /**
     * Fetches the page if it's worth it for [reason] ([vanWorthFetching]). A launch asked while a fetch is under
     * way, or one that begins while it waits its turn, takes that fetch as its own: the app opening on the vans tab
     * asks twice at once, for the app's launch and for the tab's opening ([forTab]), and that is one fetch.
     */
    suspend fun refresh(reason: FetchReason) {
        val (begunBefore, wasUnderWay) = synchronized(count) {
            unfinished++
            begun to underWay
        }
        try {
            oneFetchAtATime.withLock {
                loaded.await()
                val now = clock.instant()
                val joined = reason == FetchReason.LAUNCH && (wasUnderWay || synchronized(count) { begun > begunBefore })
                if (joined || !vanWorthFetching(reason, now, lastAttempt)) return
                lastAttempt = now
                synchronized(count) {
                    begun++
                    underWay = true
                    busy.value = true
                }
                try {
                    val before = state.value
                    state.value = fetch(now, reason)
                    if (state.value != before) store.save(state.value)
                } finally {
                    synchronized(count) { underWay = false }
                }
            }
        } finally {
            synchronized(count) { if (--unfinished == 0) busy.value = false }
        }
    }

    /** A new vans tab's fetches: one per tab on screen, so each makes its own opening fetch. */
    fun forTab(): VanTabFetches = VanTabFetches(this)

    private suspend fun fetch(now: Instant, reason: FetchReason): VanTimes {
        val html = try {
            client.page()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val outcome = if (e.causes().any { it is UnknownHostException }) FetchOutcome.OFFLINE else FetchOutcome.FAILED
            Log.i(TAG, "Van page fetch $outcome", e)
            return state.value.copy(lastOutcome = outcome)
        }
        val tabs = when (val read = VanPage.read(html)) {
            is VanPageRead.Unreadable -> {
                Log.w(TAG, "AirCare's van page couldn't be read: ${read.why}")
                return state.value.copy(lastOutcome = FetchOutcome.UNREADABLE)
            }
            is VanPageRead.Read -> read.tabs
        }
        val read = tabs.map { source ->
            if (source.pins == null) Log.w(TAG, "Van tab ${source.title} unavailable: ${source.problem}")
            val rows = source.day?.takeIf { source.pins != null }?.let { day ->
                try {
                    VanPage.rows(client.table(day))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.i(TAG, "Van table $day couldn't be fetched; ${source.title} has no statuses", e)
                    null
                }
            }
            VanPage.join(source, rows) { Log.w(TAG, it) }
        }
        Log.i(TAG, "Fetched van sites: ${read.map { it.sites?.size }} ($reason)")
        return VanTimes(VanSchedule(read, now), FetchOutcome.OK)
    }

    private fun Throwable.causes() = generateSequence(this) { it.cause }

    private companion object {
        const val TAG = "Waitlight"
    }
}

/**
 * One vans tab's ticks. The first is the tab opening ([FetchReason.LAUNCH]), whatever the time; the rest, also after
 * the tab comes back, fetch only every 15 minutes while units can be out ([FetchReason.DUE]).
 */
class VanTabFetches internal constructor(private val repository: VanRepository) {
    private val opened = AtomicBoolean(false)

    suspend fun tick() = repository.refresh(if (opened.getAndSet(true)) FetchReason.DUE else FetchReason.LAUNCH)
}
