package io.cosgriff.waitlight.ui.location

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cosgriff.waitlight.data.location.ZipCodes
import io.cosgriff.waitlight.data.settings.SettingsStore
import io.cosgriff.waitlight.domain.ZipEntry
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Why a typed ZIP code wasn't taken. */
enum class ZipError { INCOMPLETE, UNKNOWN }

/** The prompt's ZIP code field: what's typed, why it was refused, and the ZIP already saved, if any. */
data class ZipFieldUi(val text: String = "", val error: ZipError? = null, val saved: String? = null)

/** The location prompt's ZIP code entry. Location itself is asked for by the screen. */
@HiltViewModel
class LocationPromptViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val zips: ZipCodes,
) : ViewModel() {
    private val text = MutableStateFlow("")
    private val error = MutableStateFlow<ZipError?>(null)

    val ui: StateFlow<ZipFieldUi> = combine(text, error, settings.settings.map { it.zip }) { text, error, saved ->
        ZipFieldUi(text, error, saved)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS), ZipFieldUi())

    init {
        // Opened to change the ZIP: start from the saved one.
        viewModelScope.launch {
            val saved = settings.settings.first().zip
            if (saved != null && text.value.isEmpty()) text.value = saved
        }
    }

    /** Keeps digits only, at most five; a new entry clears the last error. */
    fun onZipChange(input: String) {
        text.value = input.filter { it in '0'..'9' }.take(ZIP_LENGTH)
        error.value = null
    }

    /** **Go**: a Colorado ZIP code is saved and [onSaved] runs; anything else shows why not. */
    fun go(onSaved: () -> Unit) {
        viewModelScope.launch {
            when (val entry = zips.table().check(text.value)) {
                is ZipEntry.Found -> {
                    withContext(NonCancellable) { settings.setZip(entry.place.zip) }
                    onSaved()
                }
                ZipEntry.Incomplete -> error.value = ZipError.INCOMPLETE
                ZipEntry.Unknown -> error.value = ZipError.UNKNOWN
            }
        }
    }

    /** Forgets the saved ZIP code; the list goes back to "Location off" unless location is allowed. */
    fun clearZip() {
        text.value = ""
        error.value = null
        viewModelScope.launch { withContext(NonCancellable) { settings.clearZip() } }
    }

    private companion object {
        const val ZIP_LENGTH = 5
        const val STOP_AFTER_MILLIS = 5_000L
    }
}
