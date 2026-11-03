package io.cosgriff.waitlight.data.settings

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import io.cosgriff.waitlight.domain.SortOrder
import io.cosgriff.waitlight.domain.ZipTable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

/** What the app remembers between launches. */
data class Settings(
    /** The location prompt was answered, either way, so it isn't shown on launch again. */
    val locationPromptAnswered: Boolean = false,
    /** The list's sort chip. */
    val order: SortOrder = SortOrder.NEAREST,
    /** The list's low-profile rims filter. */
    val rimsOnly: Boolean = false,
    /** A ZIP code typed on the location prompt, used for "here" while there's no location fix. */
    val zip: String? = null,
)

/** [Settings], kept on the phone in a Preferences [DataStore]. An unreadable file reads as the defaults. */
class SettingsStore(private val store: DataStore<Preferences>) {
    val settings: Flow<Settings> = store.data
        .catch { e ->
            if (e !is IOException) throw e
            Log.w(TAG, "Settings unreadable; using the defaults", e)
            emit(emptyPreferences())
        }
        .map { prefs ->
            Settings(
                locationPromptAnswered = prefs[PROMPT_ANSWERED] ?: false,
                order = prefs[ORDER]?.let { name -> SortOrder.entries.firstOrNull { it.name == name } } ?: SortOrder.NEAREST,
                rimsOnly = prefs[RIMS_ONLY] ?: false,
                zip = prefs[ZIP]?.takeIf(ZipTable::isFiveDigits),
            )
        }
        .distinctUntilChanged()

    suspend fun locationPromptAnswered() {
        store.edit { it[PROMPT_ANSWERED] = true }
    }

    suspend fun sortBy(order: SortOrder) {
        store.edit { it[ORDER] = order.name }
    }

    suspend fun rimsOnly(on: Boolean) {
        store.edit { it[RIMS_ONLY] = on }
    }

    /** Keeps [zip] (five digits, checked against the table by the caller). */
    suspend fun setZip(zip: String) {
        require(ZipTable.isFiveDigits(zip)) { "Not a ZIP code: $zip" }
        store.edit { it[ZIP] = zip }
    }

    suspend fun clearZip() {
        store.edit { it.remove(ZIP) }
    }

    private companion object {
        const val TAG = "Waitlight"
        val PROMPT_ANSWERED = booleanPreferencesKey("location_prompt_answered")

        // By name, so reordering SortOrder doesn't change what was saved.
        val ORDER = stringPreferencesKey("sort_order")
        val RIMS_ONLY = booleanPreferencesKey("low_profile_rims_only")
        val ZIP = stringPreferencesKey("zip")
    }
}
