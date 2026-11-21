package io.cosgriff.waitlight.data.vans

import android.util.Log
import io.cosgriff.waitlight.domain.VanDriveTable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * The bundled van drive-time table (ADR-0008, `assets/van_drive_minutes.bin`), read off the main thread for just
 * the sites asked for, and kept until other sites are. A table that can't be read is logged and read as empty:
 * the vans then show no drive times, never straight-line guesses.
 */
class VanDriveTimes(
    /** The table's bytes, opened fresh each time; called off the main thread. */
    private val open: () -> InputStream,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val lock = Mutex()
    private var loaded: Pair<Set<String>, VanDriveTable>? = null

    /** The table with at least [keys] read (those of them it has). */
    suspend fun table(keys: Set<String>): VanDriveTable = lock.withLock {
        loaded?.takeIf { it.first.containsAll(keys) }?.second
            ?: withContext(io) { load(keys) }.also { loaded = keys to it }
    }

    private fun load(keys: Set<String>): VanDriveTable = try {
        open().use { VanDriveTable.read(it, keys) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "The van drive-time table couldn't be read", e)
        VanDriveTable.EMPTY
    }

    companion object {
        const val ASSET = "van_drive_minutes.bin"
        private const val TAG = "Waitlight"
    }
}
