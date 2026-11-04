package io.cosgriff.waitlight.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cosgriff.waitlight.data.aircare.ListFetches
import io.cosgriff.waitlight.data.aircare.WaitTimes
import io.cosgriff.waitlight.data.aircare.WaitTimesRepository
import io.cosgriff.waitlight.data.location.DriveTimes
import io.cosgriff.waitlight.data.location.HereSource
import io.cosgriff.waitlight.data.settings.Settings
import io.cosgriff.waitlight.data.settings.SettingsStore
import io.cosgriff.waitlight.domain.DriveTable
import io.cosgriff.waitlight.domain.Here
import io.cosgriff.waitlight.domain.ListState
import io.cosgriff.waitlight.domain.RefreshButton
import io.cosgriff.waitlight.domain.SortOrder
import io.cosgriff.waitlight.domain.StationRow
import io.cosgriff.waitlight.domain.WaitThresholds
import io.cosgriff.waitlight.domain.arrange
import io.cosgriff.waitlight.domain.fastestOverall
import io.cosgriff.waitlight.domain.listState
import io.cosgriff.waitlight.domain.refreshButton
import io.cosgriff.waitlight.domain.rowsFor
import io.cosgriff.waitlight.domain.FetchReason
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

/** What the list screen shows at [now]. */
data class StationListUi(
    val now: Instant,
    val state: ListState,
    val rows: List<StationRow>,
    val hero: StationRow?,
    val refresh: RefreshButton,
    /** The newest AirCare "Last updated", or null before the first reading. */
    val newestUpdate: Instant?,
    val dataUsable: Boolean,
    val order: SortOrder,
    val rimsOnly: Boolean,
    /** Where distances are measured from: the phone, a typed ZIP code, or nowhere. */
    val here: Here?,
    /** Location is allowed, though perhaps not found yet. */
    val locationAllowed: Boolean,
    val refreshing: Boolean,
    val thresholds: WaitThresholds,
) {
    val hasLocation: Boolean get() = here != null
}

/** The list's choices, kept in [Settings] so the list opens the way it was left, and what it's waiting on. */
private data class Choices(val order: SortOrder, val rimsOnly: Boolean, val refreshing: Boolean, val locationAllowed: Boolean)

/**
 * The station list. While the screen is showing it ticks every [TICK_MILLIS] to re-age the data and give the
 * repository a chance to fetch ([ListFetches]: the list's launch fetch first, then when AirCare's next update is
 * due). A tap on refresh asks for [FetchReason.MANUAL]. The repository decides, and says while it's fetching.
 */
@HiltViewModel
class StationListViewModel @Inject constructor(
    private val waitTimes: WaitTimesRepository,
    private val hereSource: HereSource,
    private val driveTimes: DriveTimes,
    private val settings: SettingsStore,
    private val clock: Clock,
) : ViewModel() {
    private val saved = settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    private val fetches = waitTimes.forList()
    private val choices = combine(saved, waitTimes.fetching, hereSource.locationAllowed) { s, fetching, allowed ->
        Choices(s.order, s.rimsOnly, fetching, allowed)
    }

    private val ticks = flow {
        while (true) {
            emit(clock.instant())
            viewModelScope.launch { fetches.tick() }
            delay(TICK_MILLIS)
        }
    }

    val ui: StateFlow<StationListUi> = combine(ticks, waitTimes.waitTimes, hereSource.here, driveTimes.table, choices, ::build)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS),
            build(
                clock.instant(),
                waitTimes.waitTimes.value,
                hereSource.here.value,
                driveTimes.table.value,
                saved.value.let { Choices(it.order, it.rimsOnly, waitTimes.fetching.value, hereSource.locationAllowed.value) },
            ),
        )

    fun sortBy(order: SortOrder) {
        viewModelScope.launch { settings.sortBy(order) }
    }

    fun toggleRims() {
        viewModelScope.launch { settings.rimsOnly(!saved.value.rimsOnly) }
    }

    /** A tap on refresh or "Try again": looks for the phone again too. */
    fun refresh() {
        hereSource.find()
        viewModelScope.launch { waitTimes.refresh(FetchReason.MANUAL) }
    }

    private fun build(now: Instant, times: WaitTimes, here: Here?, drives: DriveTable, choices: Choices): StationListUi =
        stationListUi(now, times, here, drives, choices.order, choices.rimsOnly, choices.locationAllowed, choices.refreshing)

    private companion object {
        const val TICK_MILLIS = 30_000L
        const val STOP_AFTER_MILLIS = 5_000L
    }
}

/** The list at [now] from the waits the app knows, where "here" is and the user's choices. */
internal fun stationListUi(
    now: Instant,
    times: WaitTimes,
    here: Here?,
    drives: DriveTable,
    order: SortOrder,
    rimsOnly: Boolean,
    locationAllowed: Boolean,
    refreshing: Boolean,
): StationListUi {
    val newest = times.newestUpdate
    val state = listState(now, newest, times.lastOutcome)
    val rows = arrange(rowsFor(times.waits, here?.point, drives), order, rimsOnly, state)
    return StationListUi(
        now = now,
        state = state,
        rows = rows,
        hero = fastestOverall(rows, state, now),
        refresh = refreshButton(state, now, newest),
        newestUpdate = newest,
        dataUsable = times.dataUsable,
        order = order,
        rimsOnly = rimsOnly,
        here = here,
        locationAllowed = locationAllowed,
        refreshing = refreshing,
        thresholds = THRESHOLDS,
    )
}

private val THRESHOLDS = WaitThresholds()
