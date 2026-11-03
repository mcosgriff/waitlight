package io.cosgriff.waitlight.data.location

import android.util.Log
import io.cosgriff.waitlight.domain.GeoPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The last location the app found, in a small file in the app's own files, for the home-screen widget: it is
 * drawn with the app closed, when Android gives an app without background location access no location at all.
 * It stays on the phone and out of backups. An unreadable file reads as no location.
 */
class LastLocationStore(
    /** Called off the main thread, since finding the app's files directory can touch the disk. */
    private val file: () -> File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun load(): GeoPoint? = withContext(io) {
        try {
            val file = file()
            if (!file.exists()) return@withContext null
            val (lat, lng) = file.readText().trim().split(',').map { it.toDouble() }
            GeoPoint(lat, lng).takeIf { lat in -90.0..90.0 && lng in -180.0..180.0 }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "The last location couldn't be read", e)
            null
        }
    }

    suspend fun save(point: GeoPoint) = withContext(io) {
        try {
            val file = file()
            val partial = File(file.parentFile, "${file.name}.tmp")
            partial.writeText("${point.latitude},${point.longitude}")
            if (!partial.renameTo(file)) error("couldn't replace $file")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "The last location couldn't be saved", e)
        }
    }

    /** Forgets it, as when location is no longer allowed. */
    suspend fun clear() {
        withContext(io) { file().delete() }
    }

    companion object {
        /** In the app's files directory; `backup_rules.xml` and `data_extraction_rules.xml` name it too. */
        const val FILE_NAME = "location.txt"

        private const val TAG = "Waitlight"
    }
}
