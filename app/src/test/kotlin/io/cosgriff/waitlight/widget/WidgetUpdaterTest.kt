package io.cosgriff.waitlight.widget

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.cosgriff.waitlight.data.aircare.AirCareClient
import io.cosgriff.waitlight.data.aircare.WaitTimesRepository
import io.cosgriff.waitlight.data.aircare.WaitTimesStore
import io.cosgriff.waitlight.data.location.HereSource
import io.cosgriff.waitlight.data.location.LastLocationStore
import io.cosgriff.waitlight.data.location.PhoneFix
import io.cosgriff.waitlight.data.location.ZipCodes
import io.cosgriff.waitlight.data.settings.SettingsStore
import io.cosgriff.waitlight.domain.Badge
import io.cosgriff.waitlight.domain.WaitTimes
import io.cosgriff.waitlight.domain.WidgetContent
import io.cosgriff.waitlight.ui.AT_ARVADA
import io.cosgriff.waitlight.ui.denver
import io.cosgriff.waitlight.ui.threeWaits
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * One redraw works the widget out once and sets its next redraw once, however Glance then draws it. The home
 * screen and WorkManager are a [FakeHost]; the waits (Arvada, Aurora and Boulder, updated at 14:14) and the last
 * fix (at Arvada) are on disk, as the background finds them.
 */
class WidgetUpdaterTest {
    private class SetClock(var now: Instant) : Clock() {
        override fun instant() = now
        override fun getZone(): ZoneOffset = ZoneOffset.UTC
        override fun withZone(zone: ZoneId) = this
    }

    private class FakeHost(var placed: Boolean = true) : WidgetHost {
        var redraws = 0
        val scheduled = mutableListOf<Duration>()
        var cancels = 0

        override suspend fun anyPlaced() = placed
        override suspend fun redrawAll() { redraws++ }
        override fun redrawAfter(delay: Duration) { scheduled += delay }
        override fun cancelRedraws() { cancels++ }
    }

    private class AllowedPhone : PhoneFix {
        override val permitted: StateFlow<Boolean> = MutableStateFlow(true)
        override fun recheck() = Unit
        override suspend fun find() = null
    }

    private val updated = denver("2026-11-02T14:14")
    private val clock = SetClock(denver("2026-11-02T14:20"))
    private val host = FakeHost()

    private val dir: File = Files.createTempDirectory("widget").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val updater: WidgetUpdater = runBlocking {
        val store = WaitTimesStore({ File(dir, WaitTimesStore.FILE_NAME) }, Dispatchers.Unconfined)
        store.save(WaitTimes(threeWaits(updated)))
        val lastFix = LastLocationStore({ File(dir, LastLocationStore.FILE_NAME) }, Dispatchers.Unconfined)
        lastFix.save(AT_ARVADA)
        val repository = WaitTimesRepository(
            AirCareClient(HttpClient(MockEngine { error("the widget never fetches") })),
            clock,
            store,
            CoroutineScope(Dispatchers.Unconfined),
        )
        val settings = SettingsStore(PreferenceDataStoreFactory.create(scope = scope) { File(dir, "settings.preferences_pb") })
        val here = HereSource(AllowedPhone(), lastFix, settings, ZipCodes({ "" }, Dispatchers.Unconfined), scope)
        WidgetUpdater(host, repository, here, clock)
    }

    @After fun tearDown() {
        scope.cancel()
        dir.deleteRecursively()
    }

    @Test
    fun `one redraw works the widget out once and sets one next redraw`() = runBlocking {
        updater.update()
        // Glance then draws the widget, which asks for what to show.
        val shown = updater.current()

        assertEquals(1, host.redraws)
        // The first badges go stale a second after 14:44, 24 minutes and a second from now.
        assertEquals(listOf(Duration.ofMinutes(24).plusSeconds(1)), host.scheduled)
        assertTrue(shown.content is WidgetContent.Nearest)
    }

    @Test
    fun `with no widget placed it draws nothing and cancels the pending redraw`() = runBlocking {
        host.placed = false
        updater.update()

        assertEquals(0, host.redraws)
        assertEquals(emptyList<Duration>(), host.scheduled)
        assertEquals(1, host.cancels)
    }

    @Test
    fun `a widget drawn later reuses the drawing while its badges still hold, shown at that time`() = runBlocking {
        updater.update()
        clock.now = denver("2026-11-02T14:30")
        val shown = updater.current()

        assertEquals(denver("2026-11-02T14:30"), shown.now)
        // The running widget follows the drawing flow, so it holds what was shown.
        assertEquals(shown, updater.drawing.value)
        assertEquals(1, host.scheduled.size)
    }

    @Test
    fun `a widget drawn once its badges have gone stale is worked out again`() = runBlocking {
        updater.update()
        clock.now = denver("2026-11-02T14:50")
        val shown = updater.current()

        val rows = (shown.content as WidgetContent.Nearest).rows
        assertTrue(rows.none { it.badge is Badge.Live })
        // Stale now, so only closing time is left to redraw for: 17:30.
        assertEquals(Duration.ofMinutes(160), host.scheduled.last())
        assertEquals(2, host.scheduled.size)
    }
}
