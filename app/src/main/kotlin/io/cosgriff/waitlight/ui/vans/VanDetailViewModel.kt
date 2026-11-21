package io.cosgriff.waitlight.ui.vans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cosgriff.waitlight.data.location.HereSource
import io.cosgriff.waitlight.data.location.ZipCodes
import io.cosgriff.waitlight.data.vans.VanDriveTimes
import io.cosgriff.waitlight.data.vans.VanRepository
import io.cosgriff.waitlight.domain.VanDayContent
import io.cosgriff.waitlight.domain.VanDriveTable
import io.cosgriff.waitlight.domain.VanListState
import io.cosgriff.waitlight.domain.VanRow
import io.cosgriff.waitlight.domain.VanSiteWeek
import io.cosgriff.waitlight.domain.ZipTable
import io.cosgriff.waitlight.domain.vanSiteWeek
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
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
)

/** A van site's detail. Re-ages its status as the list does; fetching is left to the list. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class VanDetailViewModel @Inject constructor(
    vans: VanRepository,
    hereSource: HereSource,
    zips: ZipCodes,
    driveTimes: VanDriveTimes,
    private val clock: Clock,
) : ViewModel() {
    private val site = MutableStateFlow<Pair<String, LocalDate>?>(null)

    private val ticks = flow {
        while (true) {
            emit(clock.instant())
            delay(TICK_MILLIS)
        }
    }

    private val zipTable = flow { emit(zips.table()) }.onStart { emit(ZipTable.EMPTY) }
    private val drives = site.filterNotNull().mapLatest { driveTimes.table(setOf(it.first)) }.onStart { emit(VanDriveTable.EMPTY) }

    val ui: StateFlow<VanDetailUi?> = combine(ticks, vans.vans, hereSource.here, combine(drives, zipTable, ::Pair), site) { now, times, here, (drives, zipTable), site ->
        site ?: return@combine null
        val (key, date) = site
        val list = vansUi(now, times, here, drives, zipTable, date, readingOnly = false, locationAllowed = false, refreshing = false)
        val day = list.list.days.firstOrNull { it.date == date }
        val row = (day?.content as? VanDayContent.Sites)?.rows?.firstOrNull { it.site.key == key }
        VanDetailUi(now, row, times.schedule?.let { vanSiteWeek(it, key, now) }, list.state, list.list.fetchedAt, day?.isToday == true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS), null)

    fun show(key: String, date: LocalDate) {
        site.value = key to date
    }

    private companion object {
        const val TICK_MILLIS = 30_000L
        const val STOP_AFTER_MILLIS = 5_000L
    }
}
