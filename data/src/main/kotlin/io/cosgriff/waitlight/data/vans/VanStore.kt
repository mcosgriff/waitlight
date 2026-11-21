package io.cosgriff.waitlight.data.vans

import io.cosgriff.waitlight.data.AtomicJsonFile
import io.cosgriff.waitlight.domain.FetchOutcome
import io.cosgriff.waitlight.domain.GeoPoint
import io.cosgriff.waitlight.domain.VanLiveStatus
import io.cosgriff.waitlight.domain.VanSchedule
import io.cosgriff.waitlight.domain.VanSite
import io.cosgriff.waitlight.domain.VanTab
import io.cosgriff.waitlight.domain.VanTabDays
import io.cosgriff.waitlight.domain.VanTabKind
import io.cosgriff.waitlight.domain.VanTimes
import io.cosgriff.waitlight.domain.VanWeek
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

/**
 * Keeps the last good van schedule in a small JSON file in the app's own files, so the list survives a restart, as
 * `WaitTimesStore` does for the waits; left out of backups. An unreadable file is logged, discarded and read as
 * nothing.
 */
class VanStore(
    /** Called off the main thread. */
    file: () -> File,
    io: CoroutineDispatcher = Dispatchers.IO,
) {
    // Discarded when unreadable: the launch fetch gets the schedule again.
    private val stored = AtomicJsonFile(
        file,
        AtomicJsonFile.Unreadable.DISCARD,
        what = "The stored van schedule",
        io,
        read = { json.decodeFromString(Stored.serializer(), it).toVanTimes() },
        write = { json.encodeToString(Stored.serializer(), Stored.of(it)) },
    )

    suspend fun load(): VanTimes? = stored.load()

    suspend fun save(times: VanTimes) = stored.save(times)

    @Serializable
    private data class Stored(val lastOutcome: FetchOutcome, val fetchedAt: Long? = null, val tabs: List<StoredTab> = emptyList()) {
        fun toVanTimes(): VanTimes {
            val schedule = fetchedAt?.let { at -> VanSchedule(tabs.map { it.toTab() }, Instant.ofEpochMilli(at)) }
            return VanTimes(schedule, lastOutcome)
        }

        companion object {
            fun of(times: VanTimes) = Stored(
                times.lastOutcome,
                times.schedule?.fetchedAt?.toEpochMilli(),
                times.schedule?.tabs.orEmpty().map { tab ->
                    StoredTab(tab.kind.week, tab.kind.days, tab.sites?.map(StoredSite::of), tab.problem)
                },
            )
        }
    }

    @Serializable
    private data class StoredTab(val week: VanWeek, val days: VanTabDays, val sites: List<StoredSite>?, val problem: String? = null) {
        fun toTab() = VanTab(VanTabKind(week, days), sites?.map { it.toSite() }, problem)
    }

    @Serializable
    private data class StoredSite(
        val description: String,
        val lat: Double,
        val lon: Double,
        val days: List<Int>,
        val opens: String,
        val closes: String,
        val city: String,
        val status: VanLiveStatus? = null,
    ) {
        fun toSite() = VanSite(
            description,
            GeoPoint(lat, lon),
            days.map(DayOfWeek::of).toSet(),
            LocalTime.parse(opens),
            LocalTime.parse(closes),
            city,
            status,
        )

        companion object {
            fun of(site: VanSite) = StoredSite(
                site.description,
                site.point.latitude,
                site.point.longitude,
                site.days.map { it.value }.sorted(),
                site.opens.toString(),
                site.closes.toString(),
                site.city,
                site.status,
            )
        }
    }

    companion object {
        /** In the app's files directory; `backup_rules.xml` and `data_extraction_rules.xml` name it too. */
        const val FILE_NAME = "vans.json"

        private val json = AtomicJsonFile.json
    }
}
