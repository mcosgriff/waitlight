package io.cosgriff.waitlight.ui.vans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cosgriff.waitlight.data.location.HereSource
import io.cosgriff.waitlight.data.location.ZipCodes
import io.cosgriff.waitlight.data.vans.DriveByRepository
import io.cosgriff.waitlight.data.vans.VanDriveTimes
import io.cosgriff.waitlight.data.vans.VanRepository
import io.cosgriff.waitlight.data.vans.VanTabFetches
import io.cosgriff.waitlight.data.vehicles.VehicleRepository
import io.cosgriff.waitlight.domain.DriveByLog
import io.cosgriff.waitlight.domain.FetchReason
import io.cosgriff.waitlight.domain.driveByLog
import io.cosgriff.waitlight.domain.GeoPoint
import io.cosgriff.waitlight.domain.Here
import io.cosgriff.waitlight.domain.VanDayContent
import io.cosgriff.waitlight.domain.VanDriveTable
import io.cosgriff.waitlight.domain.VanList
import io.cosgriff.waitlight.domain.VanListHeader
import io.cosgriff.waitlight.domain.VanListState
import io.cosgriff.waitlight.domain.VanSite
import io.cosgriff.waitlight.domain.VanTimes
import io.cosgriff.waitlight.domain.ZipTable
import io.cosgriff.waitlight.domain.readingCount
import io.cosgriff.waitlight.domain.vanList
import io.cosgriff.waitlight.domain.vanListHeader
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/** What the vans list shows at [now]. */
data class VansUi(
    val now: Instant,
    val list: VanList,
    /** What the header, banner, hero, chips and rows show, decided in `:domain` ([vanListHeader]). */
    val header: VanListHeader,
    val refreshing: Boolean,
    /** The user's drive-by log. */
    val driveBys: DriveByLog = DriveByLog(emptyList(), 0),
    /** How many vehicles the user has saved. */
    val vehicles: Int = 0,
) {
    val state: VanListState get() = list.state
}

/** The day chip and filter, and what's being waited on. */
private data class VanChoices(val selected: LocalDate?, val readingOnly: Boolean, val refreshing: Boolean, val locationAllowed: Boolean)

/**
 * The vans list. While on screen it ticks every [TICK_MILLIS] to re-age the statuses and give the repository a
 * chance to fetch ([VanTabFetches]: the tab's opening fetch first, then every 15 minutes while units can be out). A
 * tap on refresh asks for [FetchReason.MANUAL]. Drive times are read for the day on screen only.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class VansViewModel @Inject constructor(
    private val vans: VanRepository,
    private val hereSource: HereSource,
    private val zips: ZipCodes,
    private val driveTimes: VanDriveTimes,
    private val driveByLog: DriveByRepository,
    private val vehicles: VehicleRepository,
    private val clock: Clock,
) : ViewModel() {
    private val selected = MutableStateFlow<LocalDate?>(null)
    private val readingOnly = MutableStateFlow(false)

    private val fetches = vans.forTab()

    private val ticks = flow {
        while (true) {
            emit(clock.instant())
            viewModelScope.launch { fetches.tick() }
            delay(TICK_MILLIS)
        }
    }

    private val zipTable = flow { emit(zips.table()) }.onStart { emit(ZipTable.EMPTY) }

    /** The keys of the sites on the day on screen, for the drive table. */
    private val dayKeys = combine(vans.vans, selected) { times, date -> keysFor(times, date) }.distinctUntilChanged()

    private val drives = dayKeys.mapLatest { driveTimes.table(it) }.onStart { emit(VanDriveTable.EMPTY) }

    private val choices = combine(selected, readingOnly, vans.fetching, hereSource.locationAllowed, ::VanChoices)

    private val records = combine(driveByLog.driveBys, vehicles.vehicles, ::Pair)

    private val tables = combine(drives, zipTable, records, ::Triple)

    val ui: StateFlow<VansUi> = combine(ticks, vans.vans, hereSource.here, tables, choices) { now, times, here, (drives, zipTable, records), choices ->
        val (log, vehicleList) = records
        vansUi(now, times, here, drives, zipTable, choices.selected, choices.readingOnly, choices.locationAllowed, choices.refreshing)
            .copy(driveBys = driveByLog(log, now), vehicles = vehicleList.size)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS),
        vansUi(clock.instant(), vans.vans.value, hereSource.here.value, VanDriveTable.EMPTY, ZipTable.EMPTY, null, false, hereSource.locationAllowed.value, false),
    )

    fun pick(date: LocalDate) {
        selected.value = date
    }

    fun toggleReadingOnly() {
        readingOnly.value = !readingOnly.value
    }

    /** A tap on refresh: looks for the phone again too. */
    fun refresh() {
        hereSource.find()
        viewModelScope.launch { vans.refresh(FetchReason.MANUAL) }
    }

    private fun keysFor(times: VanTimes, date: LocalDate?): Set<String> {
        val list = vanList(times, clock.instant(), null, { _, _ -> null }, { null })
        val day = list.days.firstOrNull { it.date == date } ?: list.days.firstOrNull()
        return ((day?.content as? VanDayContent.Sites)?.rows ?: emptyList()).map { it.site.key }.toSet()
    }

    private companion object {
        const val TICK_MILLIS = 30_000L
        const val STOP_AFTER_MILLIS = 5_000L
    }
}

/** The vans list at [now] from the schedule the app knows, where "here" is and the chips. */
internal fun vansUi(
    now: Instant,
    times: VanTimes,
    here: Here?,
    drives: VanDriveTable,
    zips: ZipTable,
    selected: LocalDate?,
    readingOnly: Boolean,
    locationAllowed: Boolean,
    refreshing: Boolean,
): VansUi {
    val list = vanList(
        times,
        now,
        here?.point,
        drives = { site: VanSite, from: GeoPoint -> drives.minutes(from, site.key) },
        places = { point -> zips.placeOf(point)?.place },
    )
    return VansUi(
        now = now,
        list = list,
        header = vanListHeader(list, selected, readingOnly, here, locationAllowed, refreshing),
        refreshing = refreshing,
    )
}

/** Today's reading count for the bottom bar, from the schedule alone. */
internal fun readingNow(times: VanTimes, now: Instant): Int {
    val today = vanList(times, now, null, { _, _ -> null }, { null }).days.firstOrNull { it.isToday } ?: return 0
    return readingCount(today)
}
