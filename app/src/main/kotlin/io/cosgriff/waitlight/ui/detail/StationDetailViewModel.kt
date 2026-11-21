package io.cosgriff.waitlight.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cosgriff.waitlight.data.aircare.StationRowsNow
import io.cosgriff.waitlight.domain.StationRow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * A station's detail: its row from the shared rows-at-now source ([StationRowsNow]), which re-ages its badge as the
 * list's does. Fetching is left to the list.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StationDetailViewModel @Inject constructor(private val rowsNow: StationRowsNow) : ViewModel() {
    private val stationId = MutableStateFlow<Int?>(null)

    /** The station's row, or null until [show] or for an id that isn't a station. */
    val row: StateFlow<StationRow?> = stationId.filterNotNull()
        .flatMapLatest { rowsNow.row(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS), null)

    /** The station this detail is for. */
    fun show(id: Int) {
        stationId.value = id
    }

    private companion object {
        const val STOP_AFTER_MILLIS = 5_000L
    }
}
