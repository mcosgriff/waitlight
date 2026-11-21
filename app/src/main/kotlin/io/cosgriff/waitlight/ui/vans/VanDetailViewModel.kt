package io.cosgriff.waitlight.ui.vans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cosgriff.waitlight.data.vans.DriveByRepository
import io.cosgriff.waitlight.data.vans.VanSiteAt
import io.cosgriff.waitlight.data.vans.VanSitesNow
import io.cosgriff.waitlight.data.vehicles.VehicleRepository
import io.cosgriff.waitlight.domain.DriveBy
import io.cosgriff.waitlight.domain.VanListState
import io.cosgriff.waitlight.domain.VanRow
import io.cosgriff.waitlight.domain.VanSiteWeek
import io.cosgriff.waitlight.domain.VehicleWindow
import io.cosgriff.waitlight.domain.vehicleWindows
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/** One site on one day, with its week on the page's tabs. [row] is null once the site is no longer on the page. */
data class VanDetailUi(
    val now: Instant,
    val row: VanRow?,
    val week: VanSiteWeek?,
    val state: VanListState,
    val fetchedAt: Instant?,
    val isToday: Boolean,
    /** The user's drive-bys past this site, newest first. */
    val driveBys: List<DriveBy> = emptyList(),
    /** The user's vehicles, for the drive-by's "which vehicle?". */
    val vehicles: List<VehicleWindow> = emptyList(),
)

/**
 * A van site's detail: its site from the shared van-sites-at-now source ([VanSitesNow]), which re-ages its status as
 * the list's does. Fetching is left to the list.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class VanDetailViewModel @Inject constructor(
    sitesNow: VanSitesNow,
    private val driveBys: DriveByRepository,
    vehicles: VehicleRepository,
) : ViewModel() {
    private val site = MutableStateFlow<Pair<String, LocalDate>?>(null)

    val ui: StateFlow<VanDetailUi?> = combine(site.filterNotNull().flatMapLatest { (key, date) -> sitesNow.site(key, date) }, vehicles.vehicles) { site, vehicleList ->
        vanDetailUi(site, vehicleWindows(vehicleList, site.log.entries, site.now))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS), null)

    fun show(key: String, date: LocalDate) {
        site.value = key to date
    }

    /** The user drove past the site on screen at [at], in [vehicleId] if they have vehicles. */
    fun logDriveBy(at: Instant, vehicleId: Long?) {
        val row = ui.value?.row ?: return
        viewModelScope.launch { driveBys.add(row.site.key, row.site.description, row.site.point, at, vehicleId) }
    }

    private companion object {
        const val STOP_AFTER_MILLIS = 5_000L
    }
}

/** The detail from its site at now ([VanSiteAt]) and the user's vehicles. */
internal fun vanDetailUi(site: VanSiteAt, vehicles: List<VehicleWindow>): VanDetailUi =
    VanDetailUi(site.now, site.row, site.week, site.state, site.fetchedAt, site.isToday, site.driveBys, vehicles)
