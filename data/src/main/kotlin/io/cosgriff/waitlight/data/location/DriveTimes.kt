package io.cosgriff.waitlight.data.location

import android.util.Log
import io.cosgriff.waitlight.domain.DriveTable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The bundled drive-time table (ADR-0007, `assets/drive_minutes.bin`), read once, off the main thread, as soon
 * as this is made. [table] is [DriveTable.EMPTY] until then; rows show no drive time for that moment. A table
 * that can't be read is logged and stays empty, never a crash: the list then shows miles without drive times,
 * and "Fastest overall" has nothing to add.
 */
class DriveTimes(
    /** The table's bytes; called off the main thread. */
    private val read: () -> ByteArray,
    scope: CoroutineScope,
    io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val loaded = MutableStateFlow(DriveTable.EMPTY)
    val table: StateFlow<DriveTable> = loaded.asStateFlow()

    init {
        scope.launch(io) { loaded.value = load() }
    }

    private fun load(): DriveTable = try {
        DriveTable.parse(read())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "The drive-time table couldn't be read", e)
        DriveTable.EMPTY
    }

    companion object {
        const val ASSET = "drive_minutes.bin"
        private const val TAG = "Waitlight"
    }
}
