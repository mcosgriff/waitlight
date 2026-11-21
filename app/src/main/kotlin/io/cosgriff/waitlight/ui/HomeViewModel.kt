package io.cosgriff.waitlight.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cosgriff.waitlight.data.vans.VanRepository
import io.cosgriff.waitlight.domain.FetchReason
import io.cosgriff.waitlight.ui.vans.readingNow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

/**
 * The bottom bar: fetches the van page once at launch (ADR-0001's amendment), whichever tab opens, and counts the
 * vans reading cars now for the "Roadside vans" tab's badge.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(vans: VanRepository, clock: Clock) : ViewModel() {
    init {
        viewModelScope.launch { vans.refresh(FetchReason.LAUNCH) }
    }

    private val ticks = flow {
        while (true) {
            emit(clock.instant())
            delay(TICK_MILLIS)
        }
    }

    /** How many sites are reading cars now; 0 shows no badge. */
    val readingNow: StateFlow<Int> = combine(ticks, vans.vans) { now, times -> readingNow(times, now) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS), 0)

    private companion object {
        const val TICK_MILLIS = 60_000L
        const val STOP_AFTER_MILLIS = 5_000L
    }
}
