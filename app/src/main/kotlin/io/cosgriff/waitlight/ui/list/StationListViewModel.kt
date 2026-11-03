package io.cosgriff.waitlight.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cosgriff.waitlight.data.aircare.WaitTimes
import io.cosgriff.waitlight.data.aircare.WaitTimesRepository
import io.cosgriff.waitlight.data.location.LocationSource
import io.cosgriff.waitlight.data.settings.Settings
import io.cosgriff.waitlight.data.settings.SettingsStore
import io.cosgriff.waitlight.domain.GeoPoint
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
import io.cosgriff.waitlight.domain.shouldFetch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
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
    val hasLocation: Boolean,
    /** Location is allowed, though perhaps not found yet. */
    val locationAllowed: Boolean,
    val refreshing: Boolean,
    val thresholds: WaitThresholds,
)

/** The list's choices, kept in [Settings] so the list opens the way it was left. */
private data class Choices(val order: SortOrder, val rimsOnly: Boolean, val refreshing: Boolean)

/**
 * The station list. While the screen is showing, it ticks every [TICK_MILLIS] to re-age the data, and fetches
 * when AirCare's next update is due ([shouldFetch]); a tap on refresh fetches whenever the stations are open.
 */
@HiltViewModel
class StationListViewModel @Inject constructor(
    private val waitTimes: WaitTimesRepository,
    private val location: LocationSource,
    private val settings: SettingsStore,
    private val clock: Clock,
) : ViewModel() {
    private val saved = settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    private val refreshing = MutableStateFlow(false)
    private val choices = combine(saved, refreshing) { s, r -> Choices(s.order, s.rimsOnly, r) }

    private val ticks = flow {
        while (true) {
            val now = clock.instant()
            emit(now)
            refresh(manual = false)
            delay(TICK_MILLIS)
        }
    }

    val ui: StateFlow<StationListUi> = combine(ticks, waitTimes.waitTimes, location.here, choices, ::build)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS),
            build(clock.instant(), waitTimes.waitTimes.value, location.here.value, saved.value.let { Choices(it.order, it.rimsOnly, false) }),
        )

    fun sortBy(order: SortOrder) {
        viewModelScope.launch { settings.sortBy(order) }
    }

    fun toggleRims() {
        viewModelScope.launch { settings.rimsOnly(!saved.value.rimsOnly) }
    }

    /** A tap on refresh or "Try again". */
    fun refresh() = refresh(manual = true)

    /** The screen came back, perhaps with location newly allowed. */
    fun onShown() {
        if (location.here.value == null && location.permitted()) viewModelScope.launch { location.update() }
    }

    private fun refresh(manual: Boolean) {
        val due = shouldFetch(clock.instant(), waitTimes.waitTimes.value.newestUpdate)
        val needsLocation = location.here.value == null && location.permitted()
        if (!manual && !due && !needsLocation) return
        viewModelScope.launch {
            if (manual || due) refreshing.value = true
            try {
                if (manual || needsLocation) launch { location.update() }
                waitTimes.refresh(manual)
            } finally {
                refreshing.value = false
            }
        }
    }

    private fun build(now: Instant, times: WaitTimes, here: GeoPoint?, choices: Choices): StationListUi {
        val newest = times.newestUpdate
        val state = listState(now, newest, times.lastOutcome)
        val rows = arrange(rowsFor(times.waits, here), choices.order, choices.rimsOnly, state)
        return StationListUi(
            now = now,
            state = state,
            rows = rows,
            hero = fastestOverall(rows, state, now),
            refresh = refreshButton(state, now, newest),
            newestUpdate = newest,
            dataUsable = times.dataUsable,
            order = choices.order,
            rimsOnly = choices.rimsOnly,
            hasLocation = here != null,
            locationAllowed = location.permitted(),
            refreshing = choices.refreshing,
            thresholds = THRESHOLDS,
        )
    }

    private companion object {
        const val TICK_MILLIS = 30_000L
        const val STOP_AFTER_MILLIS = 5_000L
        val THRESHOLDS = WaitThresholds()
    }
}
