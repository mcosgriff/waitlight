package io.cosgriff.waitlight.data.aircare

import io.cosgriff.waitlight.domain.FetchOutcome
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
        repository.refresh()
        val restarted = newRepository()
        assertEquals(repository.waitTimes.value, restarted.waitTimes.value)
        assertEquals(18, restarted.waitTimes.value.waits.size)
    }

    @Test
    fun `stored waits are there outside opening hours`() = runTest {
        repository.refresh()
        clock.now = denver("2026-11-08T10:00") // a Sunday
        val restarted = newRepository()
        assertFalse(restarted.refresh())
        assertEquals(18, restarted.waitTimes.value.waits.size)
        assertEquals(denver("2026-11-02T17:16"), restarted.waitTimes.value.newestUpdate)
    }

    @Test
    fun `a failed fetch is stored too`() = runTest {
        repository.refresh()
        answer = { respond("", HttpStatusCode.ServiceUnavailable) }
        repository.refresh(manual = true)
        val restarted = newRepository()
        assertEquals(FetchOutcome.FAILED, restarted.waitTimes.value.lastOutcome)
        assertEquals(18, restarted.waitTimes.value.waits.size)
    }

    @Test
    fun `stored waits that are fresh aren't fetched again`() = runTest {
        repository.refresh()
        assertFalse(newRepository().refresh())
        assertEquals(1, requests.size)
    }

    @Test
    fun `a corrupt file starts empty and is replaced by the next good read`() = runTest {
        file.writeText("{not json")
        val restarted = newRepository()
        assertTrue(restarted.waitTimes.value.waits.isEmpty())
        assertTrue(restarted.refresh())
        assertEquals(18, newRepository().waitTimes.value.waits.size)
    }

    @Test
    fun `fetches and reads the page`() = runTest {
        assertTrue(repository.refresh())
        val times = repository.waitTimes.value
        assertEquals(FetchOutcome.OK, times.lastOutcome)
        assertEquals(18, times.waits.size)
        assertEquals(denver("2026-11-02T17:16"), times.newestUpdate)
        assertEquals(AirCareClient.PAGE_URL, requests.single().url.toString())
        assertEquals(AirCareClient.USER_AGENT, requests.single().headers[HttpHeaders.UserAgent])
    }

    @Test
    fun `doesn't fetch again while the data is fresh, unless asked`() = runTest {
        repository.refresh()
        assertFalse(repository.refresh())
        assertEquals(1, requests.size)
        assertTrue(repository.refresh(manual = true))
        assertEquals(2, requests.size)
    }

    @Test
    fun `never fetches outside opening hours, even when asked`() = runTest {
        clock.now = denver("2026-11-08T10:00") // a Sunday
        assertFalse(repository.refresh())
        assertFalse(repository.refresh(manual = true))
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `an unreadable page keeps the last good waits, marked unusable`() = runTest {
        repository.refresh()
        answer = { respond("<html>Down for maintenance</html>") }
        repository.refresh(manual = true)
        val times = repository.waitTimes.value
        assertEquals(FetchOutcome.UNREADABLE, times.lastOutcome)
        assertFalse(times.dataUsable)
        assertEquals(18, times.waits.size)
    }

    @Test
    fun `an error from AirCare is a failed fetch that keeps the waits`() = runTest {
        repository.refresh()
        answer = { respond("", HttpStatusCode.ServiceUnavailable) }
        repository.refresh(manual = true)
        assertEquals(FetchOutcome.FAILED, repository.waitTimes.value.lastOutcome)
        assertEquals(18, repository.waitTimes.value.waits.size)
        assertTrue(repository.waitTimes.value.dataUsable)
    }

    @Test
    fun `no network is offline`() = runTest {
        answer = { throw UnknownHostException("aircarecolorado.com") }
        repository.refresh()
        assertEquals(FetchOutcome.OFFLINE, repository.waitTimes.value.lastOutcome)
    }

    @Test
    fun `a good page after a bad one clears the outcome`() = runTest {
        answer = { respond("<html></html>") }
        repository.refresh()
        answer = { respond(savedPage()) }
        repository.refresh(manual = true)
        assertEquals(FetchOutcome.OK, repository.waitTimes.value.lastOutcome)
    }
}
