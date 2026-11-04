package io.cosgriff.waitlight.data.location

import io.cosgriff.waitlight.domain.GeoPoint
import kotlinx.coroutines.flow.StateFlow

/**
 * The phone's fix and whether location is allowed: Android's own [android.location.LocationManager] in the app
 * ([AndroidPhoneFix], ADR-0002), a fake in tests. Only [HereSource] uses it.
 */
interface PhoneFix {
    /** Whether location is allowed, precise or approximate, as of the last [recheck]. */
    val permitted: StateFlow<Boolean>

    /** Reads the permission again. Android says nothing when it changes, so the app asks when it comes back. */
    fun recheck()

    /** Where the phone is now, or null when it can't be found or location isn't allowed. */
    suspend fun find(): GeoPoint?
}
