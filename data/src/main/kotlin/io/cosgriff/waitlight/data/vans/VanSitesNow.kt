package io.cosgriff.waitlight.data.vans

import io.cosgriff.waitlight.data.location.HereSource
import io.cosgriff.waitlight.data.location.ZipCodes
import io.cosgriff.waitlight.domain.DriveBy
import io.cosgriff.waitlight.domain.DriveByLog
import io.cosgriff.waitlight.domain.GeoPoint
import io.cosgriff.waitlight.domain.Here
import io.cosgriff.waitlight.domain.VanDay
import io.cosgriff.waitlight.domain.VanDayContent
import io.cosgriff.waitlight.domain.VanDriveTable
import io.cosgriff.waitlight.domain.VanList
import io.cosgriff.waitlight.domain.VanSite
import io.cosgriff.waitlight.domain.VanSiteDetail
import io.cosgriff.waitlight.domain.VanTimes
import io.cosgriff.waitlight.domain.ZipTable
import io.cosgriff.waitlight.domain.driveByLog
import io.cosgriff.waitlight.domain.readingCount
import io.cosgriff.waitlight.domain.vanList
import io.cosgriff.waitlight.domain.vanSiteDetail
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformLatest
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/** The van sites at [now] ([vanList]) and the drive-by log, with what they were made from. */
data class VanSitesAt(
    val now: Instant,
    val times: VanTimes,
    val here: Here?,
    val list: VanList,
    /** The user's drive-by log at [now] ([driveByLog]). */
    val driveBys: DriveByLog,
) {
    /** The day [selected], else the first. */
    fun day(selected: LocalDate?): VanDay? = list.days.firstOrNull { it.date == selected } ?: list.days.firstOrNull()

    /** The site with [key] on [date], for its detail ([vanSiteDetail]). */
    fun site(key: String, date: LocalDate): VanSiteAt =
        VanSiteAt(now, vanSiteDetail(list, times.schedule, key, date, now, driveBys), driveBys)
}

/** One site on one day at [now]. [detail] is null once the site is no longer on the page for that day. */
data class VanSiteAt(
    val now: Instant,
    val detail: VanSiteDetail?,
    /** The user's whole drive-by log, for their vehicles' windows. */
    val log: DriveByLog,
)

/** The drive-by log at [now]. */
data class DriveBysAt(val now: Instant, val log: DriveByLog)

/**
 * The one "van sites at now" source for the vans list, a site's detail, the bottom bar's reading count and the
 * drive-by log: the schedule, "here", the ZIP table, the drive table and the log, made into the list and re-aged
 * every [TICK_MILLIS] so a status changes on time with no new data. Each screen collects it in its own scope, so it
 * ticks only while a screen is showing. It reads drive times for the day on screen only ([VanDriveTimes]); the
 * reading count needs none. Fetching is left to the screens.
 */
class VanSitesNow @Inject constructor(
    private val vans: VanRepository,
    private val here: HereSource,
    private val zips: ZipCodes,
    private val driveTimes: VanDriveTimes,
    private val driveBys: DriveByRepository,
    private val clock: Clock,
) {
    private fun ticks(): Flow<Instant> = flow {
        while (true) {
            emit(clock.instant())
            delay(TICK_MILLIS)
        }
    }

    private fun zipTable(): Flow<ZipTable> = flow { emit(zips.table()) }.onStart { emit(ZipTable.EMPTY) }

    /**
     * The sites at now with [day]'s drive times (the first day while it's null), at every tick or change. A day
     * whose times aren't read yet shows first without them, then again with them.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun list(day: Flow<LocalDate?>): Flow<VanSitesAt> = flow {
        var drives = VanDriveTable.EMPTY
        val inputs = combine(ticks(), vans.vans, here.here, zipTable(), driveBys.driveBys) { now, times, here, zips, log ->
            { table: VanDriveTable -> vanSitesAt(now, times, here, table, zips, log) }
        }
        emitAll(
            combine(inputs, day, ::Pair).transformLatest { (at, selected) ->
                val sites = at(drives)
                emit(sites)
                val keys = (sites.day(selected)?.content as? VanDayContent.Sites)?.rows.orEmpty().map { it.site.key }.toSet()
                val table = driveTimes.table(keys)
                if (table !== drives) {
                    drives = table
                    emit(at(table))
                }
            },
        )
    }

    /** The site with [key] on [date] alone, as it ages, with that day's drive times. */
    fun site(key: String, date: LocalDate): Flow<VanSiteAt> = list(flowOf(date)).map { it.site(key, date) }.distinctUntilChanged()

    /** How many sites are reading cars now, for the bottom bar's badge; no drive times are read for it. */
    fun readingNow(): Flow<Int> = combine(ticks(), vans.vans) { now, times ->
        readingCount(listAt(now, times, null, VanDriveTable.EMPTY, ZipTable.EMPTY).days.firstOrNull { it.isToday })
    }.distinctUntilChanged()

    /** The drive-by log at now, at every tick or change. */
    fun driveBys(): Flow<DriveBysAt> = combine(ticks(), driveBys.driveBys) { now, log -> DriveBysAt(now, driveByLog(log, now)) }

    /** The sites as they are this moment, without drive times or places, for a screen's first frame. */
    fun current(): VanSitesAt =
        vanSitesAt(clock.instant(), vans.vans.value, here.here.value, VanDriveTable.EMPTY, ZipTable.EMPTY, driveBys.driveBys.value)

    companion object {
        /** How often the sites are re-aged while a screen shows them. */
        const val TICK_MILLIS = 30_000L
    }
}

/** The sites at [now] from the schedule, "here", the drive and ZIP tables and the log, as [VanSitesNow] makes them. */
fun vanSitesAt(now: Instant, times: VanTimes, here: Here?, drives: VanDriveTable, zips: ZipTable, driveBys: List<DriveBy>): VanSitesAt =
    VanSitesAt(now, times, here, listAt(now, times, here, drives, zips), driveByLog(driveBys, now))

private fun listAt(now: Instant, times: VanTimes, here: Here?, drives: VanDriveTable, zips: ZipTable): VanList = vanList(
    times,
    now,
    here?.point,
    drives = { site: VanSite, from: GeoPoint -> drives.minutes(from, site.key) },
    places = { point -> zips.placeOf(point)?.place },
)
