package io.cosgriff.waitlight.data.aircare

import io.cosgriff.waitlight.data.location.DriveTimes
import io.cosgriff.waitlight.data.location.HereSource
import io.cosgriff.waitlight.domain.DriveTable
import io.cosgriff.waitlight.domain.Here
import io.cosgriff.waitlight.domain.StationRow
import io.cosgriff.waitlight.domain.WaitTimes
import io.cosgriff.waitlight.domain.rowsFor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

/** Every station's row at [now] ([rowsFor]), with what it was made from. */
data class RowsNow(
    val now: Instant,
    val times: WaitTimes,
    val here: Here?,
    val drives: DriveTable,
    val rows: List<StationRow>,
)

/**
 * The one "rows at now" source for the list and the detail: the waits, "here" and the drive table, made into each
 * station's row and re-aged every [TICK_MILLIS] so a badge goes stale, or closed, on time with no new data. Each
 * screen collects it in its own scope, so it ticks only while a screen is showing; the detail asks for its
 * station's row alone ([row]).
 */
class StationRowsNow @Inject constructor(
    private val waitTimes: WaitTimesRepository,
    private val here: HereSource,
    private val drives: DriveTimes,
    private val clock: Clock,
) {
    private fun ticks(): Flow<Instant> = flow {
        while (true) {
            emit(clock.instant())
            delay(TICK_MILLIS)
        }
    }

    /** Every station's row, now and at every tick or change. */
    fun rows(): Flow<RowsNow> = combine(ticks(), waitTimes.waitTimes, here.here, drives.table) { now, times, here, drives ->
        RowsNow(now, times, here, drives, rowsFor(times, here?.point, drives, now))
    }

    /** The row for [stationId] alone, as it ages; null for an id that isn't a station. */
    fun row(stationId: Int): Flow<StationRow?> = rows().map { now -> now.rows.firstOrNull { it.station.id == stationId } }.distinctUntilChanged()

    /** The rows as they are this moment, for a screen's first frame. */
    fun current(): RowsNow {
        val now = clock.instant()
        val times = waitTimes.waitTimes.value
        val here = here.here.value
        val drives = drives.table.value
        return RowsNow(now, times, here, drives, rowsFor(times, here?.point, drives, now))
    }

    companion object {
        /** How often the rows are re-aged while a screen shows them. */
        const val TICK_MILLIS = 30_000L
    }
}
