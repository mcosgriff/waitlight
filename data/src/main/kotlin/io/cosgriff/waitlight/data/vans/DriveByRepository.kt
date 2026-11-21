package io.cosgriff.waitlight.data.vans

import io.cosgriff.waitlight.data.AtomicJsonFile
import io.cosgriff.waitlight.data.di.ApplicationScope
import io.cosgriff.waitlight.domain.DriveBy
import io.cosgriff.waitlight.domain.GeoPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import java.io.File
import java.time.Instant

/**
 * The user's drive-by log ([DriveBy]), in `drive_bys.json` in the app's files. It's the user's own record, so,
 * unlike the fetched data, it goes into backups and device transfers. Loaded off the main thread when this starts;
 * an unreadable file is logged, kept aside as `drive_bys.json.bad` so nothing is lost, and the log starts empty.
 */
class DriveByRepository(
    /** Called off the main thread. */
    file: () -> File,
    scope: CoroutineScope,
    io: CoroutineDispatcher = Dispatchers.IO,
) {
    // Kept aside when unreadable: the log is the user's own record and can't be fetched again.
    private val stored = AtomicJsonFile(
        file,
        AtomicJsonFile.Unreadable.KEEP_ASIDE,
        what = "The drive-by log",
        io,
        read = { text -> json.decodeFromString<List<Stored>>(text).map { it.toDriveBy() } },
        write = { entries -> json.encodeToString(entries.map(Stored::of)) },
    )

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

    private suspend fun load(): List<DriveBy> {
        val entries = stored.load().orEmpty()
        state.compareAndSet(emptyList(), entries)
        return entries
    }

    private suspend fun save(entries: List<DriveBy>) = stored.save(entries)

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
        /** In the app's files directory; `backup_rules.xml` and `data_extraction_rules.xml` name it too. */
        const val FILE_NAME = "drive_bys.json"
        private val json = AtomicJsonFile.json
    }
}
