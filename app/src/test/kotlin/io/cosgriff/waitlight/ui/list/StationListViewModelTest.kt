package io.cosgriff.waitlight.ui.list

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.cosgriff.waitlight.data.aircare.AirCareClient
import io.cosgriff.waitlight.data.aircare.WaitTimesRepository
import io.cosgriff.waitlight.data.aircare.WaitTimesStore
import io.cosgriff.waitlight.data.location.HereSource
import io.cosgriff.waitlight.data.location.LocationSource
import io.cosgriff.waitlight.data.location.ZipCodes
import io.cosgriff.waitlight.data.settings.SettingsStore
import io.cosgriff.waitlight.domain.GeoPoint
import io.cosgriff.waitlight.domain.Here
import io.cosgriff.waitlight.domain.ListState
import io.cosgriff.waitlight.domain.ZipPlace
import io.cosgriff.waitlight.ui.denver
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The list's tick: every 30 seconds while the screen is showing, it fetches only when [shouldFetch] says so. Robolectric
 * for [LocationSource]'s Context (no location permission, so the tick never looks for one); AirCare is a [MockEngine].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class StationListViewModelTest {
    private class SetClock(var now: Instant) : Clock() {
        override fun instant() = now
        override fun getZone(): ZoneOffset = ZoneOffset.UTC
        override fun withZone(zone: ZoneId) = this
    }

    private val dispatcher = StandardTestDispatcher()
    private val clock = SetClock(denver("2026-11-02T14:20"))
    private var fetches = 0

    // Each test starts with no stored waits and no saved settings.
    private val dir: File = Files.createTempDirectory("list").toFile()
    private val settingsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val settings = SettingsStore(
        PreferenceDataStoreFactory.create(scope = settingsScope) { File(dir, "settings.preferences_pb") },
    )

    private val repository = WaitTimesRepository(
        AirCareClient(
            HttpClient(
                MockEngine(
                    MockEngineConfig().apply {
                        // On the test's own clock, so a fetch the tick starts is done by the time it's counted.
                        dispatcher = this@StationListViewModelTest.dispatcher
                        addHandler { fetches++; respond(PAGE) }
                    },
                ),
            ) { expectSuccess = true },
        ),
        clock,
        WaitTimesStore({ File(dir, WaitTimesStore.FILE_NAME) }, Dispatchers.Unconfined),
        CoroutineScope(Dispatchers.Unconfined),
    )

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() {
        Dispatchers.resetMain()
        settingsScope.cancel()
        dir.deleteRecursively()
    }

    /** Shows the list (something collects its state) for the length of [block], then lets the ticks stop. */
    private fun TestScope.showing(block: TestScope.(StationListViewModel) -> Unit) {
        val location = LocationSource(RuntimeEnvironment.getApplication())
        val here = HereSource(location, settings, ZipCodes({ ZIPS }, Dispatchers.Unconfined), settingsScope)
        val viewModel = StationListViewModel(repository, location, here, settings, clock)
        val screen = launch { viewModel.ui.collect {} }
        runCurrent()
        block(viewModel)
        screen.cancel()
        // Past WhileSubscribed's grace period, so the ticking stops with the test.
        advanceTimeBy(STOP_AFTER_MILLIS)
        runCurrent()
    }

    private fun TestScope.awaitUi(viewModel: StationListViewModel, until: (StationListUi) -> Boolean): StationListUi {
        repeat(200) {
            runCurrent()
            viewModel.ui.value.takeIf(until)?.let { return it }
            Thread.sleep(10)
        }
        error("The list never got there: ${viewModel.ui.value.here}")
    }

    private fun TestScope.tick(at: String) {
        clock.now = denver(at)
        advanceTimeBy(TICK_MILLIS)
        runCurrent()
    }

    @Test
    fun `the tick doesn't fetch while the data is fresh, and does once AirCare's next update is due`() = runTest(dispatcher) {
        repository.refresh(manual = true) // "Last updated" 2:14 PM, read at 2:20
        assertEquals(1, fetches)

        showing { viewModel ->
            assertEquals(ListState.LIVE, viewModel.ui.value.state)
            assertEquals(1, fetches)
            tick("2026-11-02T14:21")
            tick("2026-11-02T14:29:14")
            assertEquals(1, fetches)

            // Fifteen minutes after AirCare's 2:14:15 PM.
            tick("2026-11-02T14:29:15")
            assertEquals(2, fetches)
        }
    }

    @Test
    fun `the tick never fetches while the stations are closed, even with no data at all`() = runTest(dispatcher) {
        clock.now = denver("2026-11-08T10:00") // a Sunday
        showing { viewModel ->
            assertEquals(ListState.CLOSED, viewModel.ui.value.state)
            tick("2026-11-08T10:01")
            tick("2026-11-08T10:30")
            assertEquals(0, fetches)
        }
    }

    @Test
    fun `a saved ZIP is here when location isn't allowed`() = runTest(dispatcher) {
        settings.setZip("80003")
        showing { viewModel ->
            // The settings and the table are read on other threads; wait for them.
            val ui = awaitUi(viewModel) { it.here != null }
            assertEquals(Here.Zip(ZipPlace("80003", GeoPoint(39.8254, -105.0644), "Arvada")), ui.here)
            assertEquals("Arvada", ui.rows.first().station.name)
        }
    }

    @Test
    fun `the tick fetches straight away when open with no data yet`() = runTest(dispatcher) {
        showing {
            assertEquals(1, fetches)
        }
    }

    private companion object {
        const val TICK_MILLIS = 30_000L
        const val STOP_AFTER_MILLIS = 5_001L

        /** AirCare's page cut down to one station, Arvada, 13 minutes, as of 2:14 PM on 2026-11-02. */
        const val ZIPS = "80003|39.8254|-105.0644|Arvada"

        val PAGE = """
            <html><body><script>
            var latlngs = [{"name":"Arvada: Wait is 13 mins","locationID":38,"infoWindow":"<h3>Arvada: Wait is 13 mins<\/h3><b>Hours of Operation<\/b>: 8am to 5:30pm (M-F),<br>8am to 1pm (Sat), closed Sunday and state holidays<br \/><br \/><b>Last updated<\/b>: 11\/2\/2026 2:14:15 PM"}];
            </script></body></html>
        """.trimIndent()
    }
}
