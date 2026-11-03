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
 * The bundled table of Colorado ZIP codes (ADR-0005, `assets/colorado_zips.txt`) and their outlines
 * (`assets/colorado_zip_shapes.txt`), read once, off the main thread, the first time they're asked for. A table
 * that can't be read is logged and read as empty, never a crash: ZIP entry then finds no ZIP, and the header says
 * "Near you". Outlines that can't be read are logged and left out: the header then names the nearest centroid's ZIP.
 */
class ZipCodes(
    /** The table's text; called off the main thread. */
    private val read: () -> String,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    /** The outlines' text, or null for none; called off the main thread. */
    private val readOutlines: (() -> String)? = null,
) {
    private val lock = Mutex()

    @Volatile private var loaded: ZipTable? = null

    suspend fun table(): ZipTable = loaded ?: lock.withLock {
        loaded ?: withContext(io) { load() }.also { loaded = it }
    }

    private fun load(): ZipTable {
        val table = try {
            ZipTable.parse(read())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "The ZIP code table couldn't be read", e)
            return ZipTable.EMPTY
        }
        val outlines = readOutlines ?: return table
        return try {
            ZipTable.withOutlines(table, outlines())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "The ZIP code outlines couldn't be read", e)
            table
        }
    }

    companion object {
        const val ASSET = "colorado_zips.txt"
        const val OUTLINES_ASSET = "colorado_zip_shapes.txt"
        private const val TAG = "Waitlight"
    }
}
