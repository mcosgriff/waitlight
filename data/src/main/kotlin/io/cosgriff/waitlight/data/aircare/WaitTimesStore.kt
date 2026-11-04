package io.cosgriff.waitlight.data.aircare

import android.util.Log
import io.cosgriff.waitlight.domain.FetchOutcome
import io.cosgriff.waitlight.domain.Stations
import io.cosgriff.waitlight.domain.WaitReading
import io.cosgriff.waitlight.domain.WaitTimes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant

/**
 * Keeps the last [WaitTimes] in a small JSON file in the app's own files, so the waits survive a restart. The
 * file is left out of backups: it's minutes old at best. Nothing read from it is trusted: an unreadable file is
 * logged and read as no waits, and a reading for a station no longer in [Stations] is dropped.
 */
class WaitTimesStore(
    /** Called off the main thread, since finding the app's files directory can touch the disk. */
    private val file: () -> File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    /** The stored waits, or null when there are none or they can't be read. */
    suspend fun load(): WaitTimes? = withContext(io) {
        val stored = try {
            val file = file()
            if (!file.exists()) return@withContext null
            json.decodeFromString<Stored>(file.readText())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Stored waits couldn't be read; starting empty", e)
            return@withContext null
        }
        stored.toWaitTimes()
    }

    /** Replaces the stored waits with [times]. A failed write is logged; the waits in memory are still right. */
    suspend fun save(times: WaitTimes) = withContext(io) {
        try {
            val file = file()
            val partial = File(file.parentFile, "${file.name}.tmp")
            partial.writeText(json.encodeToString(Stored.serializer(), Stored.of(times)))
            if (!partial.renameTo(file)) error("couldn't replace $file")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "The waits couldn't be saved", e)
        }
    }

    @Serializable
    private data class Stored(val lastOutcome: FetchOutcome, val waits: List<StoredWait>) {
        fun toWaitTimes(): WaitTimes {
            val read = waits.mapNotNull { stored ->
                if (Stations[stored.id] == null) {
                    Log.i(TAG, "Dropped a stored wait for station ${stored.id}, no longer in Stations")
                    return@mapNotNull null
                }
                val reading = stored.reading() ?: run {
                    Log.w(TAG, "Dropped a stored wait for station ${stored.id} that couldn't be read")
                    return@mapNotNull null
                }
                stored.id to (reading to Instant.ofEpochMilli(stored.updatedAt))
            }.toMap()
            return WaitTimes(read, lastOutcome)
        }

        companion object {
            fun of(times: WaitTimes) = Stored(
                lastOutcome = times.lastOutcome,
                waits = times.waits.map { (id, wait) ->
                    val (reading, updatedAt) = wait
                    val (kind, minutes) = when (reading) {
                        is WaitReading.Minutes -> Kind.MINUTES to reading.minutes
                        is WaitReading.MoreThan -> Kind.MORE_THAN to reading.minutes
                        WaitReading.NoEstimate -> Kind.NO_ESTIMATE to null
                    }
                    StoredWait(id, kind, minutes, updatedAt.toEpochMilli())
                },
            )
        }
    }

    @Serializable
    private data class StoredWait(
        val id: Int,
        val kind: Kind,
        val minutes: Int? = null,
        /** AirCare's "Last updated" for this station, in epoch milliseconds. */
        val updatedAt: Long,
    ) {
        fun reading(): WaitReading? = when (kind) {
            Kind.MINUTES -> minutes?.takeIf { it >= 0 }?.let(WaitReading::Minutes)
            Kind.MORE_THAN -> minutes?.let(WaitReading::MoreThan)
            Kind.NO_ESTIMATE -> WaitReading.NoEstimate
        }
    }

    @Serializable
    private enum class Kind {
        @SerialName("minutes") MINUTES,
        @SerialName("moreThan") MORE_THAN,
        @SerialName("noEstimate") NO_ESTIMATE,
    }

    companion object {
        /** In the app's files directory; `backup_rules.xml` and `data_extraction_rules.xml` name it too. */
        const val FILE_NAME = "waits.json"

        private const val TAG = "Waitlight"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
