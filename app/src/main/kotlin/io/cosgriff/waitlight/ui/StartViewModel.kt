package io.cosgriff.waitlight.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cosgriff.waitlight.data.location.HereSource
import io.cosgriff.waitlight.data.settings.SettingsStore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Which screen the app opens on: the list once location is allowed or the prompt was answered. */
@HiltViewModel
class StartViewModel @Inject constructor(
    private val settings: SettingsStore,
    here: HereSource,
) : ViewModel() {
    /** The first screen, or null until the settings are read. */
    val start: StateFlow<NavKey?> = combine(settings.settings, here.locationAllowed) { saved, allowed ->
        if (allowed || saved.locationPromptAnswered) StationList else LocationPrompt
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Either answer on the prompt: it isn't shown on launch again. */
    fun locationPromptAnswered() {
        viewModelScope.launch { withContext(NonCancellable) { settings.locationPromptAnswered() } }
    }
}
