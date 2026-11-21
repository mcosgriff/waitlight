package io.cosgriff.waitlight.ui.vans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cosgriff.waitlight.data.location.HereSource
import io.cosgriff.waitlight.data.vans.VanRepository
import io.cosgriff.waitlight.data.vans.VanSitesAt
import io.cosgriff.waitlight.data.vans.VanSitesNow
import io.cosgriff.waitlight.data.vans.VanTabFetches
import io.cosgriff.waitlight.data.vans.vanSitesAt
import io.cosgriff.waitlight.data.vehicles.VehicleRepository
import io.cosgriff.waitlight.domain.DriveByLog
import io.cosgriff.waitlight.domain.FetchReason
import io.cosgriff.waitlight.domain.Here
import io.cosgriff.waitlight.domain.VanDriveTable
import io.cosgriff.waitlight.domain.VanList
import io.cosgriff.waitlight.domain.VanListHeader
import io.cosgriff.waitlight.domain.VanListState
import io.cosgriff.waitlight.domain.VanTimes
import io.cosgriff.waitlight.domain.ZipTable
import io.cosgriff.waitlight.domain.vanListHeader
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
 * The vans list. Its sites come from the shared van-sites-at-now source ([VanSitesNow]), which re-ages the statuses
 * while the screen shows and reads drive times for the day on screen only; alongside, every [FETCH_TICK_MILLIS] it
 * gives the repository a chance to fetch ([VanTabFetches]: the tab's opening fetch first, then every 15 minutes while
 * units can be out). A tap on refresh asks for [FetchReason.MANUAL].
 */
@HiltViewModel
class VansViewModel @Inject constructor(
    private val vans: VanRepository,
    private val hereSource: HereSource,
    sitesNow: VanSitesNow,
    vehicles: VehicleRepository,
) : ViewModel() {
    private val selected = MutableStateFlow<LocalDate?>(null)
    private val readingOnly = MutableStateFlow(false)

    private val fetches = vans.forTab()

    /** The list's chances to fetch, while it's on screen. */
    private val fetchTicks = flow {
        while (true) {
            emit(Unit)
            viewModelScope.launch { fetches.tick() }
            delay(FETCH_TICK_MILLIS)
        }
    }

    private val choices = combine(selected, readingOnly, vans.fetching, hereSource.locationAllowed, ::VanChoices)

    val ui: StateFlow<VansUi> = combine(sitesNow.list(selected), choices, vehicles.vehicles, fetchTicks) { sites, choices, vehicleList, _ ->
        vansUi(sites, choices.selected, choices.readingOnly, choices.locationAllowed, choices.refreshing).copy(vehicles = vehicleList.size)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS),
        vansUi(sitesNow.current(), null, false, hereSource.locationAllowed.value, false),
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

    private companion object {
        const val FETCH_TICK_MILLIS = 30_000L
        const val STOP_AFTER_MILLIS = 5_000L
    }
}

/** The vans list from the sites at now ([VanSitesAt]) and the chips. */
internal fun vansUi(sites: VanSitesAt, selected: LocalDate?, readingOnly: Boolean, locationAllowed: Boolean, refreshing: Boolean): VansUi = VansUi(
    now = sites.now,
    list = sites.list,
    header = vanListHeader(sites.list, selected, readingOnly, sites.here, locationAllowed, refreshing),
    refreshing = refreshing,
    driveBys = sites.driveBys,
)

/** The vans list at [now] from the schedule, "here", the tables and the chips, as [VanSitesNow] makes it: for tests and previews. */
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
): VansUi = vansUi(vanSitesAt(now, times, here, drives, zips, emptyList()), selected, readingOnly, locationAllowed, refreshing)
