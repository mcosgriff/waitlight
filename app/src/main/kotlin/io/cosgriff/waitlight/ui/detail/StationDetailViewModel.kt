package io.cosgriff.waitlight.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cosgriff.waitlight.data.aircare.WaitTimesRepository
import io.cosgriff.waitlight.data.location.HereSource
import io.cosgriff.waitlight.domain.StationRow
import io.cosgriff.waitlight.domain.WaitThresholds
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

/** Every station's row at [now]; the screen shows the one it was opened for. */
data class StationDetailUi(
    val now: Instant,
    val rows: Map<Int, StationRow>,
    val dataUsable: Boolean,
    val thresholds: WaitThresholds,
)

/** A station's detail. Re-ages its badge as the list does; fetching is left to the list. */
@HiltViewModel
class StationDetailViewModel @Inject constructor(
    waitTimes: WaitTimesRepository,
    hereSource: HereSource,
    private val clock: Clock,
) : ViewModel() {
    private val ticks = flow {
        while (true) {
            emit(clock.instant())
            delay(TICK_MILLIS)
        }
    }

    val ui: StateFlow<StationDetailUi> = combine(ticks, waitTimes.waitTimes, hereSource.here) { now, times, here ->
        StationDetailUi(now, rowsFor(times.waits, here?.point).associateBy { it.station.id }, times.dataUsable, THRESHOLDS)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS),
        waitTimes.waitTimes.value.let { times ->
            StationDetailUi(
                clock.instant(),
                rowsFor(times.waits, hereSource.here.value?.point).associateBy { it.station.id },
                times.dataUsable,
                THRESHOLDS,
            )
        },
    )

    private companion object {
        const val TICK_MILLIS = 30_000L
        const val STOP_AFTER_MILLIS = 5_000L
        val THRESHOLDS = WaitThresholds()
    }
}
