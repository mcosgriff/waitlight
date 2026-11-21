package io.cosgriff.waitlight.data.location

import io.cosgriff.waitlight.data.AtomicJsonFile
import io.cosgriff.waitlight.domain.GeoPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The last location the app found, in a small file in the app's own files, for the home-screen widget: it is
 * drawn with the app closed, when Android gives an app without background location access no location at all.
 * It stays on the phone and out of backups. An unreadable file is discarded and reads as no location.
 */
class LastLocationStore(
    /** Called off the main thread, since finding the app's files directory can touch the disk. */
    private val file: () -> File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    // Not JSON but "39.8277,-105.0656", as the file has always held. Discarded when unreadable: the next fix
    // replaces it.
    private val stored = AtomicJsonFile(
        file,
        AtomicJsonFile.Unreadable.DISCARD,
        what = "The last location",
        io,
        read = { text ->
            val (lat, lng) = text.trim().split(',').map { it.toDouble() }
            require(lat in -90.0..90.0 && lng in -180.0..180.0) { "not a place: $text" }
            GeoPoint(lat, lng)
        },
        write = { point -> "${point.latitude},${point.longitude}" },
    )

    suspend fun load(): GeoPoint? = stored.load()

    suspend fun save(point: GeoPoint) = stored.save(point)

    /** Forgets it, as when location is no longer allowed. */
    suspend fun clear() {
        withContext(io) { file().delete() }
    }

    companion object {
        /** In the app's files directory; `backup_rules.xml` and `data_extraction_rules.xml` name it too. */
        const val FILE_NAME = "location.txt"
    }
}
