package io.cosgriff.waitlight.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
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
 * Where the phone is, from Android's own [LocationManager] (ADR-0002: no Play services). The app asks for precise
 * location, so the miles and drive estimates are right; approximate, which Android blurs by a mile or two, works
 * too. The location stays on the phone.
 */
@Singleton
class AndroidPhoneFix @Inject constructor(@param:ApplicationContext private val context: Context) : PhoneFix {
    private val manager: LocationManager = context.getSystemService(LocationManager::class.java)
    private val state = MutableStateFlow(allowed())

    override val permitted: StateFlow<Boolean> = state.asStateFlow()

    override fun recheck() {
        state.value = allowed()
    }

    private fun allowed(): Boolean = PERMISSIONS.any { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    // The permission is checked first; one revoked in between throws, which is caught.
    @SuppressLint("MissingPermission")
    override suspend fun find(): GeoPoint? = try {
        if (!allowed()) {
            null
        } else {
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
            val found = current ?: enabled.mapNotNull { manager.getLastKnownLocation(it) }.maxByOrNull { it.elapsedRealtimeNanos }
            found?.let { GeoPoint(it.latitude, it.longitude) }
        }
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
