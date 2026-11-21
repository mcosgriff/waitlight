package io.cosgriff.waitlight.data.vans

import io.cosgriff.waitlight.domain.AirCareHours
import io.cosgriff.waitlight.domain.FetchOutcome
import io.cosgriff.waitlight.domain.FetchReason
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class VanRepositoryTest {
    private class SetClock(var now: Instant) : Clock() {
        override fun instant() = now
        override fun getZone(): ZoneOffset = ZoneOffset.UTC
        override fun withZone(zone: ZoneId) = this
    }

    private fun denver(text: String): Instant = LocalDateTime.parse(text).atZone(AirCareHours.zone).toInstant()

    private val clock = SetClock(denver("2026-11-20T16:52"))
    private val requests = mutableListOf<HttpRequestData>()

    /** The saved page, as AirCare served it: HTTP 500, since its last tab is broken. */
    private var page: suspend MockRequestHandleScope.() -> HttpResponseData =
        { respond(savedVanPage(), HttpStatusCode.InternalServerError) }

    private val dir: File = Files.createTempDirectory("vans").toFile()
    private val file = File(dir, VanStore.FILE_NAME)

    private fun newRepository() = VanRepository(
        VanClient(
            HttpClient(
                MockEngine { request ->
                    requests += request
                    val day = request.url.parameters["day"]?.toInt()
                    if (day == null) page() else respond(savedVanTable(day))
                },
            ) { expectSuccess = true },
        ),
        clock,
        VanStore({ file }, Dispatchers.Unconfined),
        CoroutineScope(Dispatchers.Unconfined),
    )

    private val repository = newRepository()

    @After
    fun deleteFiles() {
        dir.deleteRecursively()
    }

    @Test
    fun `reads the page and its three tables, accepting the 500 a broken tab causes`() = runTest {
        repository.refresh(FetchReason.LAUNCH)
        val vans = repository.vans.value
        assertEquals(FetchOutcome.OK, vans.lastOutcome)
        assertEquals(listOf(25, 20, 31, null), vans.schedule!!.tabs.map { it.sites?.size })
        assertEquals(clock.now, vans.schedule!!.fetchedAt)
        assertEquals(
            listOf(null, "58", "60", "59"),
            requests.map { it.url.parameters["day"] },
        )
        assertTrue(requests.all { it.headers["User-Agent"]!!.startsWith("Waitlight") })
        assertTrue(requests.all { it.url.host == "aircarecolorado.com" })
    }

    @Test
    fun `the schedule survives a restart`() = runTest {
        repository.refresh(FetchReason.LAUNCH)
        assertEquals(repository.vans.value, newRepository().vans.value)
    }

    @Test
    fun `an unreadable page keeps the last good schedule`() = runTest {
        repository.refresh(FetchReason.LAUNCH)
        val good = repository.vans.value.schedule
        page = { respond(savedVanPage().replace("var latlngs", "var pins")) }
        clock.now = clock.now.plus(Duration.ofMinutes(15))
        repository.refresh(FetchReason.DUE)
        assertEquals(FetchOutcome.UNREADABLE, repository.vans.value.lastOutcome)
        assertEquals(good, repository.vans.value.schedule)
    }

    @Test
    fun `a failed fetch keeps the schedule and says so`() = runTest {
        repository.refresh(FetchReason.LAUNCH)
        page = { respondError(HttpStatusCode.BadGateway) }
        clock.now = clock.now.plus(Duration.ofMinutes(15))
        repository.refresh(FetchReason.DUE)
        assertEquals(FetchOutcome.FAILED, repository.vans.value.lastOutcome)
        assertNotNull(repository.vans.value.schedule)
    }

    @Test
    fun `fetches no more than every 15 minutes, and only while units are out`() = runTest {
        repository.refresh(FetchReason.LAUNCH)
        val first = requests.size
        clock.now = clock.now.plus(Duration.ofMinutes(10))
        repository.refresh(FetchReason.DUE)
        assertEquals(first, requests.size)
        clock.now = denver("2026-11-20T19:30")
        repository.refresh(FetchReason.DUE)
        assertEquals(first, requests.size)
        repository.refresh(FetchReason.LAUNCH)
        assertTrue(requests.size > first)
    }
}
