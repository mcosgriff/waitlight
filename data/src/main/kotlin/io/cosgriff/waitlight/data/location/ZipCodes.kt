package io.cosgriff.waitlight.data.location

import android.util.Log
import io.cosgriff.waitlight.domain.ZipTable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The bundled table of Colorado ZIP codes (ADR-0005, `assets/colorado_zips.txt`), read once, off the main thread,
 * the first time it's asked for. A table that can't be read is logged and read as empty, never a crash: ZIP entry
 * then finds no ZIP, and the header says "Near you".
 */
class ZipCodes(
    /** The table's text; called off the main thread. */
    private val read: () -> String,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val lock = Mutex()

    @Volatile private var loaded: ZipTable? = null

    suspend fun table(): ZipTable = loaded ?: lock.withLock {
        loaded ?: withContext(io) { load() }.also { loaded = it }
    }

    private fun load(): ZipTable = try {
        ZipTable.parse(read())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "The ZIP code table couldn't be read", e)
        ZipTable.EMPTY
    }

    companion object {
        const val ASSET = "colorado_zips.txt"
        private const val TAG = "Waitlight"
    }
}
