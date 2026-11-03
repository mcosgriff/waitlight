package io.cosgriff.waitlight.data.location

import io.cosgriff.waitlight.data.di.ApplicationScope
import io.cosgriff.waitlight.data.settings.SettingsStore
import io.cosgriff.waitlight.domain.GeoPoint
import io.cosgriff.waitlight.domain.Here
import io.cosgriff.waitlight.domain.here
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where distances are measured from, for the list and the detail: the phone's fix when location is allowed and
 * found, otherwise the saved ZIP code's centroid, otherwise nowhere ([here]). Nothing leaves the phone.
 */
@Singleton
class HereSource @Inject constructor(
    private val location: LocationSource,
    private val settings: SettingsStore,
    private val zips: ZipCodes,
    @param:ApplicationScope scope: CoroutineScope,
) {
    /** Null until the ZIP table is read (off the main thread), then updated on each new fix or ZIP. */
    val here: StateFlow<Here?> = combine(
        location.here,
        settings.settings.map { it.zip }.distinctUntilChanged(),
        flow { emit(zips.table()) },
    ) { fix: GeoPoint?, zip: String?, table -> here(location.permitted(), fix, zip, table) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * "Here" right now, by the same rule, for work done in the background (the widget): the fix found last, kept
     * on disk ([LocationSource.lastKnown]), since a background app gets no new one; otherwise the saved ZIP.
     */
    suspend fun current(): Here? =
        here(location.permitted(), location.lastKnown(), settings.settings.first().zip, zips.table())
}
