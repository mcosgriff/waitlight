package io.cosgriff.waitlight.data.aircare

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.cosgriff.waitlight.data.location.DriveTimes
import io.cosgriff.waitlight.data.location.HereSource
import io.cosgriff.waitlight.data.location.LastLocationStore
import io.cosgriff.waitlight.data.location.PhoneFix
import io.cosgriff.waitlight.data.location.ZipCodes
import io.cosgriff.waitlight.data.settings.SettingsStore
import io.cosgriff.waitlight.domain.Badge
import io.cosgriff.waitlight.domain.FetchReason
import io.cosgriff.waitlight.domain.GeoPoint
import io.cosgriff.waitlight.domain.StationRow
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The shared rows at now, as the detail reads them: its station's row ages with the tick, going stale 30 minutes
 * after AirCare's "Last updated" and closed at closing time, with no new data.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StationRowsNowTest {
    private class SetClock(var now: Instant) : Clock() {
        override fun instant() = now
        override fun getZone(): ZoneOffset = ZoneOffset.UTC
        override fun withZone(zone: ZoneId) = this
    }

    private class NoPhone : PhoneFix {
        override val permitted: StateFlow<Boolean> = MutableStateFlow(false)
        override fun recheck() = Unit
        override suspend fun find(): GeoPoint? = null
    }

    @get:Rule val folder = TemporaryFolder()
    private val settingsScope = CoroutineScope(Dispatchers.Unconfined)

    @After fun tearDown() = settingsScope.cancel()

    @Test
    fun `the detail's row goes stale, then closed, on the tick`() = runTest {
        // The saved page of Monday 2026-11-02, with Arvada's "Last updated" moved an hour back to 4:14 PM, so it can
        // go stale before closing.
        val page = savedPage().replace("11\\/2\\/2026 5:14:15 PM", "11\\/2\\/2026 4:14:15 PM")
        check(page != savedPage())
        val clock = SetClock(denver("2026-11-02T16:20"))
        val waits = WaitTimesRepository(
            AirCareClient(HttpClient(MockEngine { respond(page) }) { expectSuccess = true }),
            clock,
            WaitTimesStore({ File(folder.root, WaitTimesStore.FILE_NAME) }, Dispatchers.Unconfined),
            CoroutineScope(Dispatchers.Unconfined),
        )
        waits.refresh(FetchReason.LAUNCH)
        val settings = SettingsStore(PreferenceDataStoreFactory.create(scope = settingsScope) { File(folder.root, "settings.preferences_pb") })
        val here = HereSource(
            NoPhone(),
            LastLocationStore({ File(folder.root, LastLocationStore.FILE_NAME) }, Dispatchers.Unconfined),
            settings,
            ZipCodes({ "" }, Dispatchers.Unconfined),
            CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
        )
        val drives = DriveTimes({ ByteArray(0) }, CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined)
        val rowsNow = StationRowsNow(waits, here, drives, clock)

        val seen = mutableListOf<StationRow?>()
        val detail = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { rowsNow.row(ARVADA).collect { seen += it } }
        runCurrent()
        assertTrue(seen.last()!!.badge is Badge.Live)

        // Over 30 minutes after AirCare's time: stale at the next tick, with no new data.
        clock.now = denver("2026-11-02T16:50")
        advanceTimeBy(StationRowsNow.TICK_MILLIS)
        runCurrent()
        assertTrue(seen.last()!!.badge is Badge.Stale)

        clock.now = denver("2026-11-02T17:30")
        advanceTimeBy(StationRowsNow.TICK_MILLIS)
        runCurrent()
        assertEquals(Badge.Closed, seen.last()!!.badge)
        assertEquals(ARVADA, seen.last()!!.station.id)
        detail.cancel()
    }

    private companion object {
        const val ARVADA = 38
    }
}
