package io.cosgriff.waitlight.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.cosgriff.waitlight.domain.GeoPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Where the phone is, from Android's own [LocationManager] (ADR-0002: no Play services). Approximate location is
 * enough to sort stations by miles, so that is all the app asks for. The location stays on the phone.
 */
@Singleton
class LocationSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val lastLocation: LastLocationStore,
) {
    private val manager: LocationManager = context.getSystemService(LocationManager::class.java)
    private val state = MutableStateFlow<GeoPoint?>(null)

    /** The last location found, or null before one is (or without permission). */
    val here: StateFlow<GeoPoint?> = state.asStateFlow()

    fun permitted(): Boolean = PERMISSIONS.any { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    /**
     * Looks for the phone's location and keeps it in [here], and on disk for the widget ([lastKnown]). Keeps the
     * old one if none is found. Without permission, forgets both.
     */
    suspend fun update() {
        if (!permitted()) {
            state.value = null
            lastLocation.clear()
            return
        }
        find()?.let { found ->
            val point = GeoPoint(found.latitude, found.longitude)
            state.value = point
            lastLocation.save(point)
        }
    }

    /**
     * Where the phone was last found, for the home-screen widget: [here], or the last location saved by an
     * earlier run of the app, since a widget drawn in the background gets no location of its own. Null without
     * permission.
     */
    suspend fun lastKnown(): GeoPoint? {
        if (!permitted()) return null
        return here.value ?: lastLocation.load()
    }

    // permitted() is checked first; a permission revoked in between throws, which is caught.
    @SuppressLint("MissingPermission")
    private suspend fun find(): Location? = try {
        val enabled = manager.getProviders(true)
        val provider = PROVIDERS.firstOrNull { it in enabled }
        val current = provider?.let { name ->
            withTimeoutOrNull(TIMEOUT_MILLIS) {
                suspendCancellableCoroutine { cont ->
                    val signal = CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    manager.getCurrentLocation(name, signal, context.mainExecutor) { cont.resume(it) }
                }
            }
        }
        current ?: enabled.mapNotNull { manager.getLastKnownLocation(it) }.maxByOrNull { it.elapsedRealtimeNanos }
    } catch (e: SecurityException) {
        Log.i(TAG, "Location permission went away", e)
        null
    }

    private companion object {
        const val TAG = "Waitlight"
        const val TIMEOUT_MILLIS = 10_000L
        val PERMISSIONS = listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)

        // Android's fused provider first (not Play services'), then the network's, then GPS.
        val PROVIDERS = listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
    }
}
