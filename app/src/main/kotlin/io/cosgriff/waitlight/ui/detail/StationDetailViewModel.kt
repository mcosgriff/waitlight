package io.cosgriff.waitlight.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cosgriff.waitlight.data.aircare.WaitTimesRepository
import io.cosgriff.waitlight.data.location.DriveTimes
import io.cosgriff.waitlight.data.location.HereSource
import io.cosgriff.waitlight.domain.DriveTable
import io.cosgriff.waitlight.domain.GeoPoint
import io.cosgriff.waitlight.domain.StationRow
import io.cosgriff.waitlight.domain.WaitTimes
import io.cosgriff.waitlight.domain.rowsFor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

/** Every station's row at the latest tick, badge and all; the screen shows the one it was opened for. */
data class StationDetailUi(val rows: Map<Int, StationRow>)

/** A station's detail. Re-ages its badge as the list does; fetching is left to the list. */
@HiltViewModel
class StationDetailViewModel @Inject constructor(
    waitTimes: WaitTimesRepository,
    hereSource: HereSource,
    driveTimes: DriveTimes,
    private val clock: Clock,
) : ViewModel() {
    private val ticks = flow {
        while (true) {
            emit(clock.instant())
            delay(TICK_MILLIS)
        }
    }

    val ui: StateFlow<StationDetailUi> = combine(ticks, waitTimes.waitTimes, hereSource.here, driveTimes.table) { now, times, here, drives ->
        detailUi(now, times, here?.point, drives)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS),
        detailUi(clock.instant(), waitTimes.waitTimes.value, hereSource.here.value?.point, driveTimes.table.value),
    )

    private fun detailUi(now: Instant, times: WaitTimes, from: GeoPoint?, drives: DriveTable) =
        StationDetailUi(rowsFor(times, from, drives, now).associateBy { it.station.id })

    private companion object {
        const val TICK_MILLIS = 30_000L
        const val STOP_AFTER_MILLIS = 5_000L
    }
}
