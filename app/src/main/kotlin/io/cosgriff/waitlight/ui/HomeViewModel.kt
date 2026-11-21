package io.cosgriff.waitlight.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cosgriff.waitlight.data.vans.VanRepository
import io.cosgriff.waitlight.data.vans.VanSitesNow
import io.cosgriff.waitlight.domain.FetchReason
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The bottom bar: fetches the van page once at launch (ADR-0001's amendment), whichever tab opens, and counts the
 * vans reading cars now for the "Roadside vans" tab's badge, from the same source and tick as the vans list
 * ([VanSitesNow]).
 */
@HiltViewModel
class HomeViewModel @Inject constructor(vans: VanRepository, sitesNow: VanSitesNow) : ViewModel() {
    init {
        viewModelScope.launch { vans.refresh(FetchReason.LAUNCH) }
    }

    /** How many sites are reading cars now; 0 shows no badge. */
    val readingNow: StateFlow<Int> = sitesNow.readingNow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS), 0)

    private companion object {
        const val STOP_AFTER_MILLIS = 5_000L
    }
}
