package io.cosgriff.waitlight.data.location

import io.cosgriff.waitlight.data.di.ApplicationScope
import io.cosgriff.waitlight.data.settings.SettingsStore
import io.cosgriff.waitlight.domain.GeoPoint
import io.cosgriff.waitlight.domain.Here
import io.cosgriff.waitlight.domain.here
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where distances are measured from, for the list, the detail and the widget: the phone's fix when location is
 * allowed and found, otherwise the saved ZIP code's centroid, otherwise nowhere ([here]). It finds the fix itself
 * while the app is in front ([onForeground]) and on a tap on refresh ([find]), keeps the last one on disk
 * for the widget ([LastLocationStore]), and forgets both when location is no longer allowed. Nothing leaves the
 * phone.
 */
@Singleton
class HereSource @Inject constructor(
    private val phone: PhoneFix,
    private val lastFix: LastLocationStore,
    private val settings: SettingsStore,
    private val zips: ZipCodes,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val fix = MutableStateFlow<GeoPoint?>(null)

    /** Guards [finding] and writes to [fix], so a search that ends after location goes can't bring the fix back. */
    private val lock = Any()

    /** The search under way, if any. */
    private var finding: Job? = null

    /** Location is allowed, though perhaps not found yet. */
    val locationAllowed: StateFlow<Boolean> = phone.permitted

    /** Null until the ZIP table is read (off the main thread), then updated on each new fix, ZIP or permission. */
    val here: StateFlow<Here?> = combine(
        phone.permitted,
        fix,
        settings.settings.map { it.zip }.distinctUntilChanged(),
        flow { emit(zips.table()) },
    ) { permitted, fix, zip, table -> here(permitted, fix, zip, table) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    init {
        scope.launch {
            phone.permitted.collect { permitted -> if (!permitted) forget() }
        }
    }

    /**
     * The app is in front again: location may have been allowed, or turned back on, meanwhile. Rereads the
     * permission and, while it's allowed and no fix is found, looks for the phone every [RETRY_MILLIS] until
     * [onBackground], since location services turned on from the quick-settings shade don't pause the app.
     */
    fun onForeground() {
        phone.recheck()
        start {
            while (fix.value == null && phone.permitted.value) {
                look()
                if (fix.value == null) delay(RETRY_MILLIS)
            }
        }
    }

    /** The app has gone to the background, where Android gives it no fix: stops looking. */
    fun onBackground() {
        synchronized(lock) { finding?.cancel() }
    }

    /** Looks for the phone again, as a tap on refresh does; keeps the old fix if none is found. */
    fun find() {
        if (phone.permitted.value) start { look() }
    }

    /** Starts [search] unless a search is already under way. */
    private fun start(search: suspend () -> Unit) {
        synchronized(lock) {
            if (finding?.isActive == true) return
            finding = scope.launch { search() }
        }
    }

    private suspend fun look() {
        val found = phone.find() ?: return
        val searching = currentCoroutineContext().job
        synchronized(lock) {
            if (!searching.isActive || !phone.permitted.value) return
            fix.value = found
        }
        lastFix.save(found)
    }

    /**
     * "Here" right now, by the same rule, for work done in the background (the widget): the fix found last, kept
     * on disk, since a background app gets no new one; otherwise the saved ZIP.
     */
    suspend fun current(): Here? {
        phone.recheck()
        val permitted = phone.permitted.value
        val known = if (permitted) fix.value ?: lastFix.load() else null
        return here(permitted, known, settings.settings.first().zip, zips.table())
    }

    private suspend fun forget() {
        synchronized(lock) {
            finding?.cancel()
            fix.value = null
        }
        lastFix.clear()
    }

    private companion object {
        /** As often as the list used to tick. */
        const val RETRY_MILLIS = 30_000L
    }
}
