package io.cosgriff.waitlight.data.vehicles

import io.cosgriff.waitlight.data.AtomicJsonFile
import io.cosgriff.waitlight.domain.ReminderKind
import io.cosgriff.waitlight.domain.Vehicle
import io.cosgriff.waitlight.domain.renewing
import io.cosgriff.waitlight.domain.settled
import io.cosgriff.waitlight.domain.withReminder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import java.io.File
import java.time.Instant
import java.time.YearMonth

/**
 * The user's vehicles ([Vehicle]), in `vehicles.json` in the app's files. Like the drive-by log they are the user's
 * own record, so they go into backups and device transfers. Loaded off the main thread when this starts; an
 * unreadable file is logged, kept aside as `vehicles.json.bad` so nothing is lost, and the list starts empty.
 */
class VehicleRepository(
    /** Called off the main thread. */
    file: () -> File,
    scope: CoroutineScope,
    io: CoroutineDispatcher = Dispatchers.IO,
) {
    // Kept aside when unreadable: the vehicles are the user's own record and can't be fetched again.
    private val stored = AtomicJsonFile(
        file,
        AtomicJsonFile.Unreadable.KEEP_ASIDE,
        what = "The vehicles",
        io,
        read = { text -> json.decodeFromString<List<Stored>>(text).map { it.toVehicle() } },
        write = { vehicles -> json.encodeToString(vehicles.map(Stored::of)) },
    )

    private val state = MutableStateFlow<List<Vehicle>>(emptyList())
    private val lock = Mutex()

    /** The vehicles, oldest first; empty until the file is read. */
    val vehicles: StateFlow<List<Vehicle>> = state.asStateFlow()

    /** As [vehicles], from once the file is read: an empty list here means there are none. */
    val loadedVehicles: Flow<List<Vehicle>> = flow {
        loaded.await()
        emitAll(state)
    }

    private val loaded = scope.async(io) { load() }

    /** Waits for the file to be read. */
    suspend fun loaded(): List<Vehicle> {
        loaded.await()
        return state.value
    }

    /**
     * Saves a new vehicle and returns its id. With [remind], it gets [ReminderKind.DEFAULTS]; reminders whose time
     * has already passed at [now] are skipped, not sent late.
     */
    suspend fun add(description: String, renews: YearMonth, remind: Boolean, now: Instant): Long {
        var id = 0L
        change { vehicles ->
            id = (vehicles.maxOfOrNull { it.id } ?: 0) + 1
            vehicles + Vehicle(id, description.trim(), renews, if (remind) ReminderKind.DEFAULTS else emptySet()).settled(now)
        }
        return id
    }

    /** A new description or renewal month; a new renewal starts its reminders afresh, skipping those already past. */
    suspend fun update(id: Long, description: String, renews: YearMonth, now: Instant) = edit(id) {
        it.copy(description = description.trim()).renewing(renews, now)
    }

    suspend fun setReminder(id: Long, kind: ReminderKind, on: Boolean, now: Instant) = edit(id) { it.withReminder(kind, on, now) }

    /** Marks each vehicle's reminders in [settled] sent. */
    suspend fun markSent(settled: Map<Long, Set<ReminderKind>>) {
        if (settled.isEmpty()) return
        change { vehicles -> vehicles.map { v -> settled[v.id]?.let { v.copy(sent = v.sent + it) } ?: v } }
    }

    /** Removes the vehicle. Its drive-bys stay in the log. */
    suspend fun remove(id: Long) = change { vehicles -> vehicles.filterNot { it.id == id } }

    private suspend fun edit(id: Long, edit: (Vehicle) -> Vehicle) = change { vehicles -> vehicles.map { if (it.id == id) edit(it) else it } }

    // Saved before it's shown: the reminder check reschedules itself on the change, which can stop the worker that
    // made it, and a reminder marked sent must be on disk by then or it would go out again.
    private suspend fun change(edit: (List<Vehicle>) -> List<Vehicle>) = lock.withLock {
        loaded.await()
        val next = edit(state.value)
        save(next)
        state.value = next
    }

    private suspend fun load(): List<Vehicle> {
        val vehicles = stored.load().orEmpty()
        state.compareAndSet(emptyList(), vehicles)
        return vehicles
    }

    private suspend fun save(vehicles: List<Vehicle>) = stored.save(vehicles)

    /** One vehicle as the file keeps it. Reminder names the app doesn't know (from a newer version) are dropped. */
    @Serializable
    private data class Stored(
        val id: Long,
        val description: String,
        /** "2027-07". */
        val renews: String,
        val reminders: List<String> = emptyList(),
        val sent: List<String> = emptyList(),
    ) {
        fun toVehicle() = Vehicle(id, description, YearMonth.parse(renews), kinds(reminders), kinds(sent))

        companion object {
            fun of(v: Vehicle) = Stored(v.id, v.description, v.renews.toString(), v.reminders.map { it.name }.sorted(), v.sent.map { it.name }.sorted())

            private fun kinds(names: List<String>): Set<ReminderKind> = names.mapNotNull { name -> ReminderKind.entries.firstOrNull { it.name == name } }.toSet()
        }
    }

    companion object {
        const val FILE_NAME = "vehicles.json"
        private val json = AtomicJsonFile.json
    }
}
