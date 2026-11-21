package io.cosgriff.waitlight.data

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * One small file in the app's own files, read whole and replaced whole: a save writes `<name>.tmp` and renames it
 * over the file, so a crash mid-write leaves the last good file, never half of one. Nothing here throws but
 * cancellation; a failure is logged, as "[what] couldn't be read" or "… saved".
 *
 * What happens to a file that can't be read ([read] throws, on bad JSON or on contents the store won't take) is
 * the store's choice, [unreadable]: fetched data is [Unreadable.DISCARD]ed, the user's own records are
 * [Unreadable.KEEP_ASIDE]. JSON, in [json], is what every store but `location.txt` keeps.
 */
internal class AtomicJsonFile<T : Any>(
    /** Called off the main thread, since finding the app's files directory can touch the disk. */
    private val file: () -> File,
    private val unreadable: Unreadable,
    /** What the file holds, for the log: "The drive-by log". */
    private val what: String,
    private val io: CoroutineDispatcher,
    /** The file's text to its value; throws when it can't be. */
    private val read: (String) -> T,
    private val write: (T) -> String,
) {
    enum class Unreadable {
        /** Deleted, and read as nothing: for data the app can fetch or find again. */
        DISCARD,

        /** Renamed to `<name>.bad` so nothing is lost, and read as nothing: for the user's own records. */
        KEEP_ASIDE,
    }

    /** The stored value, or null when there's no file or it can't be read. */
    suspend fun load(): T? = withContext(io) {
        val file = try {
            file()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "$what couldn't be found", e)
            return@withContext null
        }
        try {
            if (!file.exists()) return@withContext null
            read(file.readText())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            setAside(file, e)
            null
        }
    }

    /** Replaces the stored value with [value]. A failed write is logged; the value in memory is still right. */
    suspend fun save(value: T) {
        withContext(io) {
            var partial: File? = null
            try {
                val file = file()
                partial = File(file.parentFile, "${file.name}.tmp")
                partial.writeText(write(value))
                if (!partial.renameTo(file)) error("couldn't replace $file")
            } catch (e: CancellationException) {
                partial?.delete()
                throw e
            } catch (e: Exception) {
                partial?.delete()
                Log.w(TAG, "$what couldn't be saved", e)
            }
        }
    }

    private fun setAside(file: File, why: Exception) {
        when (unreadable) {
            Unreadable.DISCARD -> {
                Log.w(TAG, "$what couldn't be read; discarded, starting empty", why)
                if (!file.delete()) Log.w(TAG, "$what couldn't be deleted")
            }
            Unreadable.KEEP_ASIDE -> {
                val bad = File(file.parentFile, "${file.name}.bad")
                Log.w(TAG, "$what couldn't be read; kept aside as ${bad.name}, starting empty", why)
                if (!file.renameTo(bad)) Log.w(TAG, "$what couldn't be kept aside")
            }
        }
    }

    companion object {
        /** How every store's JSON is read and written: fields a newer version added are skipped. */
        val json = Json { ignoreUnknownKeys = true }

        private const val TAG = "Waitlight"
    }
}
