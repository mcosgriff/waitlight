package io.cosgriff.waitlight.data.vans

import android.util.Log
import io.cosgriff.waitlight.data.di.ApplicationScope
import io.cosgriff.waitlight.domain.DriveBy
import io.cosgriff.waitlight.domain.GeoPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant

/**
 * The user's drive-by log ([DriveBy]), in `drive_bys.json` in the app's files. It's the user's own record, so,
 * unlike the fetched data, it goes into backups and device transfers. Loaded off the main thread when this starts;
 * an unreadable file is logged, kept aside as `drive_bys.json.bad` so nothing is lost, and the log starts empty.
 */
class DriveByRepository(
    /** Called off the main thread. */
    private val file: () -> File,
    scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val state = MutableStateFlow<List<DriveBy>>(emptyList())
    private val lock = Mutex()

    val driveBys: StateFlow<List<DriveBy>> = state.asStateFlow()

    private val loaded = scope.async(io) { load() }

    /** Logs a drive-by past [description] at [point] (site [siteKey]) at [at], by [vehicleId] if one is known. */
    suspend fun add(siteKey: String, description: String, point: GeoPoint, at: Instant, vehicleId: Long? = null) = change { entries ->
        val id = (entries.maxOfOrNull { it.id } ?: 0) + 1
        entries + DriveBy(id, siteKey, description, point, at, vehicleId)
    }

    /** Waits for the log to be read from its file. */
    suspend fun loaded(): List<DriveBy> {
        loaded.await()
        return state.value
    }

    suspend fun remove(id: Long) = change { entries -> entries.filterNot { it.id == id } }

    private suspend fun change(edit: (List<DriveBy>) -> List<DriveBy>) = lock.withLock {
        loaded.await()
        state.value = edit(state.value)
        save(state.value)
    }

    private fun load(): List<DriveBy> {
        val entries = try {
            val file = file()
            if (!file.exists()) emptyList() else json.decodeFromString<List<Stored>>(file.readText()).map { it.toDriveBy() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "The drive-by log couldn't be read; kept aside, starting empty", e)
            runCatching { file().renameTo(File(file().parentFile, "${FILE_NAME}.bad")) }
            emptyList()
        }
        state.compareAndSet(emptyList(), entries)
        return entries
    }

    private suspend fun save(entries: List<DriveBy>) = withContext(io) {
        try {
            val file = file()
            val partial = File(file.parentFile, "${file.name}.tmp")
            partial.writeText(json.encodeToString(entries.map(Stored::of)))
            if (!partial.renameTo(file)) error("couldn't replace $file")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "The drive-by log couldn't be saved", e)
        }
    }

    /** One entry as the file keeps it. [vehicleId] came with vehicles; entries saved before them have none. */
    @Serializable
    private data class Stored(
        val id: Long,
        val siteKey: String,
        val description: String,
        val lat: Double,
        val lon: Double,
        val at: Long,
        val vehicleId: Long? = null,
    ) {
        fun toDriveBy() = DriveBy(id, siteKey, description, GeoPoint(lat, lon), Instant.ofEpochMilli(at), vehicleId)

        companion object {
            fun of(d: DriveBy) = Stored(d.id, d.siteKey, d.description, d.point.latitude, d.point.longitude, d.at.toEpochMilli(), d.vehicleId)
        }
    }

    companion object {
        const val FILE_NAME = "drive_bys.json"
        private const val TAG = "Waitlight"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
