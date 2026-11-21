package io.cosgriff.waitlight.data.vans

import android.util.Log
import io.cosgriff.waitlight.data.di.ApplicationScope
import io.cosgriff.waitlight.data.fetchFailure
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
import java.time.Clock
import java.time.Instant
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

    val vans: StateFlow<VanTimes> = state.asStateFlow()

    /** True while a fetch is under way. */
    val fetching: StateFlow<Boolean> = busy.asStateFlow()

    private val loaded = scope.async {
        store.load()?.let { stored -> state.compareAndSet(VanTimes(), stored) }
    }

    suspend fun refresh(reason: FetchReason) {
        oneFetchAtATime.withLock {
            loaded.await()
            val now = clock.instant()
            if (!vanWorthFetching(reason, now, lastAttempt)) return
            lastAttempt = now
            busy.value = true
            try {
                val before = state.value
                state.value = fetch(now, reason)
                if (state.value != before) store.save(state.value)
            } finally {
                busy.value = false
            }
        }
    }

    private suspend fun fetch(now: Instant, reason: FetchReason): VanTimes {
        val html = try {
            client.page()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val outcome = fetchFailure(e)
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

    private companion object {
        const val TAG = "Waitlight"
    }
}
