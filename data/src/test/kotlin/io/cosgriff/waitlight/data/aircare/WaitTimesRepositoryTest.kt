package io.cosgriff.waitlight.data.aircare

import io.cosgriff.waitlight.domain.FetchOutcome
import io.cosgriff.waitlight.domain.FetchReason
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.net.UnknownHostException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class WaitTimesRepositoryTest {
    private class SetClock(var now: Instant) : Clock() {
        override fun instant() = now
        override fun getZone(): ZoneOffset = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId) = this
    }

    private val requests = mutableListOf<HttpRequestData>()
    private var answer: suspend MockRequestHandleScope.() -> HttpResponseData = { respond(savedPage()) }

    // Six minutes after the saved page's newest time, Monday 2026-11-02: open, and the data is fresh.
    private val clock = SetClock(denver("2026-11-02T17:20"))

    private val dir: File = Files.createTempDirectory("waits").toFile()
    private val file = File(dir, WaitTimesStore.FILE_NAME)

    // Unconfined throughout: the stored waits have loaded by the time the constructor returns.
    private fun newRepository() = WaitTimesRepository(
        AirCareClient(
            HttpClient(MockEngine { request -> requests += request; answer() }) { expectSuccess = true },
        ),
        clock,
        WaitTimesStore({ file }, Dispatchers.Unconfined),
        CoroutineScope(Dispatchers.Unconfined),
    )

    private val repository = newRepository()

    @After
    fun deleteFiles() {
        dir.deleteRecursively()
    }

    @Test
    fun `the waits survive a restart`() = runTest {
        repository.refresh(FetchReason.DUE)
        val restarted = newRepository()
        assertEquals(repository.waitTimes.value, restarted.waitTimes.value)
        assertEquals(18, restarted.waitTimes.value.waits.size)
    }

    @Test
    fun `current waits for the stored ones, for a widget drawn just after a restart`() = runTest {
        repository.refresh(FetchReason.DUE)
        val restarted = WaitTimesRepository(
            AirCareClient(HttpClient(MockEngine { respond(savedPage()) }) { expectSuccess = true }),
            clock,
            WaitTimesStore({ file }, StandardTestDispatcher(testScheduler)),
            CoroutineScope(StandardTestDispatcher(testScheduler)),
        )
        // Not loaded yet...
        assertEquals(0, restarted.waitTimes.value.waits.size)
        // ...but current() waits for the load.
        assertEquals(18, restarted.current().waits.size)
    }

    @Test
    fun `stored waits are there outside opening hours`() = runTest {
        repository.refresh(FetchReason.DUE)
        clock.now = denver("2026-11-08T10:00") // a Sunday
        val restarted = newRepository()
        restarted.refresh(FetchReason.DUE)
        assertEquals(1, requests.size)
        assertEquals(18, restarted.waitTimes.value.waits.size)
        assertEquals(denver("2026-11-02T17:16"), restarted.waitTimes.value.newestUpdate)
    }

    @Test
    fun `a failed fetch is stored too`() = runTest {
        repository.refresh(FetchReason.DUE)
        answer = { respond("", HttpStatusCode.ServiceUnavailable) }
        repository.refresh(FetchReason.MANUAL)
        val restarted = newRepository()
        assertEquals(FetchOutcome.FAILED, restarted.waitTimes.value.lastOutcome)
        assertEquals(18, restarted.waitTimes.value.waits.size)
    }

    @Test
    fun `stored waits that are fresh aren't fetched again`() = runTest {
        repository.refresh(FetchReason.DUE)
        newRepository().refresh(FetchReason.DUE)
        assertEquals(1, requests.size)
    }

    @Test
    fun `a corrupt file starts empty and is replaced by the next good read`() = runTest {
        file.writeText("{not json")
        val restarted = newRepository()
        assertTrue(restarted.waitTimes.value.waits.isEmpty())
        restarted.refresh(FetchReason.DUE)
        assertEquals(18, newRepository().waitTimes.value.waits.size)
    }

    @Test
    fun `fetches and reads the page`() = runTest {
        repository.refresh(FetchReason.DUE)
        val times = repository.waitTimes.value
        assertEquals(FetchOutcome.OK, times.lastOutcome)
        assertEquals(18, times.waits.size)
        assertEquals(denver("2026-11-02T17:16"), times.newestUpdate)
        assertEquals(AirCareClient.PAGE_URL, requests.single().url.toString())
        assertEquals(AirCareClient.USER_AGENT, requests.single().headers[HttpHeaders.UserAgent])
    }

    @Test
    fun `doesn't fetch again while the data is fresh, unless asked`() = runTest {
        repository.refresh(FetchReason.DUE)
        repository.refresh(FetchReason.DUE)
        assertEquals(1, requests.size)
        repository.refresh(FetchReason.MANUAL)
        assertEquals(2, requests.size)
    }

    @Test
    fun `outside opening hours, fetches only at launch`() = runTest {
        clock.now = denver("2026-11-08T10:00") // a Sunday
        repository.refresh(FetchReason.DUE)
        repository.refresh(FetchReason.MANUAL)
        assertTrue(requests.isEmpty())
        repository.refresh(FetchReason.LAUNCH)
        assertEquals(1, requests.size)
    }

    @Test
    fun `the launch fetch happens once per list`() = runTest {
        clock.now = denver("2026-11-08T10:00") // a Sunday
        val list = repository.forList()
        list.tick()
        list.tick()
        assertEquals(1, requests.size)
        // The list opened again, after the app was left.
        repository.forList().tick()
        assertEquals(2, requests.size)
    }

    @Test
    fun `after its launch fetch, a list's ticks fetch when AirCare's next update is due`() = runTest {
        val list = repository.forList()
        list.tick() // "Last updated" 5:16 PM, read at 5:20
        clock.now = denver("2026-11-02T17:25")
        list.tick()
        assertEquals(1, requests.size)
        clock.now = denver("2026-11-03T09:00") // the next morning
        list.tick()
        assertEquals(2, requests.size)
    }

    @Test
    fun `a tap while closed never shows fetching`() = runTest {
        clock.now = denver("2026-11-08T10:00") // a Sunday
        val seen = watchFetching()
        repository.refresh(FetchReason.MANUAL)
        assertEquals(listOf(false), seen)
    }

    @Test
    fun `fetching shows while a fetch runs, and not after`() = runTest {
        val seen = watchFetching()
        repository.refresh(FetchReason.DUE)
        assertEquals(listOf(false, true, false), seen)
    }

    @Test
    fun `overlapping refreshes keep fetching true until the last one finishes`() = runTest {
        val release = Channel<Unit>()
        answer = { release.receive(); respond(savedPage()) }
        val seen = watchFetching()
        val tick = launch { repository.refresh(FetchReason.DUE) }
        val tap = launch { repository.refresh(FetchReason.MANUAL) }
        runCurrent()
        release.send(Unit) // the tick's fetch
        release.send(Unit) // the tap's, which waited for it
        tick.join()
        tap.join()
        assertEquals(2, requests.size)
        assertEquals(listOf(false, true, false), seen)
    }


    /** Every value [WaitTimesRepository.fetching] takes from now on, the current one first. */
    private fun TestScope.watchFetching(): List<Boolean> {
        val seen = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.fetching.collect { seen += it } }
        return seen
    }

    @Test
    fun `stations that say they're closed keep their last good waits`() = runTest {
        repository.refresh(FetchReason.DUE)
        val open = repository.waitTimes.value.waits
        clock.now = denver("2026-11-02T19:50")
        answer = { respond(closedPage()) }
        repository.refresh(FetchReason.LAUNCH)
        assertEquals(open, repository.waitTimes.value.waits)
        assertEquals(FetchOutcome.OK, repository.waitTimes.value.lastOutcome)
    }

    @Test
    fun `an unreadable page keeps the last good waits, marked unusable`() = runTest {
        repository.refresh(FetchReason.DUE)
        answer = { respond("<html>Down for maintenance</html>") }
        repository.refresh(FetchReason.MANUAL)
        val times = repository.waitTimes.value
        assertEquals(FetchOutcome.UNREADABLE, times.lastOutcome)
        assertFalse(times.dataUsable)
        assertEquals(18, times.waits.size)
    }

    @Test
    fun `an error from AirCare is a failed fetch that keeps the waits`() = runTest {
        repository.refresh(FetchReason.DUE)
        answer = { respond("", HttpStatusCode.ServiceUnavailable) }
        repository.refresh(FetchReason.MANUAL)
        assertEquals(FetchOutcome.FAILED, repository.waitTimes.value.lastOutcome)
        assertEquals(18, repository.waitTimes.value.waits.size)
        assertTrue(repository.waitTimes.value.dataUsable)
    }

    @Test
    fun `no network is offline`() = runTest {
        answer = { throw UnknownHostException("aircarecolorado.com") }
        repository.refresh(FetchReason.DUE)
        assertEquals(FetchOutcome.OFFLINE, repository.waitTimes.value.lastOutcome)
    }

    @Test
    fun `a good page after a bad one clears the outcome`() = runTest {
        answer = { respond("<html></html>") }
        repository.refresh(FetchReason.DUE)
        answer = { respond(savedPage()) }
        repository.refresh(FetchReason.MANUAL)
        assertEquals(FetchOutcome.OK, repository.waitTimes.value.lastOutcome)
    }
}
