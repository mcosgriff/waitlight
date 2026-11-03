package io.cosgriff.waitlight.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.cosgriff.waitlight.domain.SortOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val file get() = File(folder.root, "settings.preferences_pb")

    /** Opens the file in its own scope, as a fresh launch would; [use] runs, then the store is closed. */
    private suspend fun <T> withStore(use: suspend (SettingsStore) -> T): T {
        val scope = CoroutineScope(UnconfinedTestDispatcher())
        try {
            return use(SettingsStore(PreferenceDataStoreFactory.create(scope = scope) { file }))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `a first launch has the defaults`() = runTest {
        assertEquals(Settings(), withStore { it.settings.first() })
    }

    @Test
    fun `an answered prompt is remembered across launches`() = runTest {
        withStore { it.locationPromptAnswered() }
        assertEquals(true, withStore { it.settings.first() }.locationPromptAnswered)
    }

    @Test
    fun `the sort order and rims filter are remembered across launches`() = runTest {
        withStore {
            it.sortBy(SortOrder.FASTEST_OVERALL)
            it.rimsOnly(true)
        }
        assertEquals(Settings(order = SortOrder.FASTEST_OVERALL, rimsOnly = true), withStore { it.settings.first() })

        withStore {
            it.sortBy(SortOrder.NEAREST)
            it.rimsOnly(false)
        }
        assertEquals(Settings(), withStore { it.settings.first() })
    }

    @Test
    fun `one setting doesn't touch the others`() = runTest {
        withStore {
            it.rimsOnly(true)
            it.locationPromptAnswered()
        }
        assertEquals(Settings(locationPromptAnswered = true, rimsOnly = true), withStore { it.settings.first() })
    }

    @Test
    fun `an unreadable file reads as the defaults`() = runTest {
        file.writeBytes(byteArrayOf(0x7f, 0x01, 0x02, 0x03))
        assertEquals(Settings(), withStore { it.settings.first() })
    }
}
