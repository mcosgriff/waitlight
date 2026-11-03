package io.cosgriff.waitlight.data.aircare

import io.cosgriff.waitlight.domain.AirCareHours
import io.cosgriff.waitlight.domain.WaitReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime

/** The page as AirCare served it on 2026-11-02 (`src/test/resources/aircare`). */
internal fun savedPage(): String =
    checkNotNull(AirCarePageTest::class.java.getResource("/aircare/wait-times-2026-11-02.html")).readText()

/** The same page after closing that day: every station "Station Closed". */
internal fun closedPage(): String =
    checkNotNull(AirCarePageTest::class.java.getResource("/aircare/wait-times-2026-11-02-closed.html")).readText()

internal fun denver(text: String): Instant = LocalDateTime.parse(text).atZone(AirCareHours.zone).toInstant()

class AirCarePageTest {
    private fun read(html: String) = AirCarePage.read(html) as PageRead.Read

    @Test
    fun `reads every station on the saved page`() {
        val read = read(savedPage())
        assertEquals(18, read.waits.size)
        assertTrue(read.hoursAsStated)
        assertEquals(WaitReading.Minutes(10) to denver("2026-11-02T17:14:15"), read.waits[38]) // Arvada
        assertEquals(WaitReading.Minutes(0) to denver("2026-11-02T17:13:36"), read.waits[40]) // Golden
        assertEquals(WaitReading.MoreThan(60) to denver("2026-11-02T17:04:47"), read.waits[50]) // Parker
        // Longmont words its wait and its time differently from the rest.
        assertEquals(WaitReading.NoEstimate to denver("2026-11-02T17:16"), read.waits[43])
    }

    @Test
    fun `a page where every station says it's closed reads, with no waits`() {
        val read = read(closedPage())
        assertTrue(read.waits.isEmpty())
        assertTrue(read.hoursAsStated)
    }

    @Test
    fun `a station that says it's closed has no reading, and the rest read`() {
        val page = savedPage().replace(""""name":"Arvada: Wait is 10 mins"""", """"name":"Arvada: Station Closed"""")
        val read = read(page)
        assertEquals(17, read.waits.size)
        assertEquals(null, read.waits[38])
    }

    @Test
    fun `new wording makes the page unreadable`() {
        val page = savedPage().replace(""""name":"Arvada: Wait is 10 mins"""", """"name":"Arvada: Closed for weather"""")
        assertTrue(AirCarePage.read(page) is PageRead.Unreadable)
    }

    @Test
    fun `a page without the station array is unreadable`() {
        assertTrue(AirCarePage.read("<html><body>Down for maintenance</body></html>") is PageRead.Unreadable)
        assertTrue(AirCarePage.read("<script>var latlngs = [];</script>") is PageRead.Unreadable)
        assertTrue(AirCarePage.read("<script>var latlngs = [{\"name\":</script>") is PageRead.Unreadable)
    }

    @Test
    fun `a station without a readable time makes the page unreadable`() {
        // The page escapes its slashes as JSON may: "11\/2\/2026".
        val page = savedPage().replace("""11\/2\/2026 5:14:15 PM""", "today")
        assertTrue(page != savedPage())
        assertTrue(AirCarePage.read(page) is PageRead.Unreadable)
    }

    @Test
    fun `changed hours are noticed but the waits still read`() {
        val read = read(savedPage().replaceFirst("8am to 5:30pm (M-F)", "7am to 6pm (M-F)"))
        assertFalse(read.hoursAsStated)
        assertEquals(18, read.waits.size)
    }

    @Test
    fun `a station the app doesn't know is left out`() {
        val read = read(savedPage().replace("\"locationID\":38", "\"locationID\":99"))
        assertEquals(17, read.waits.size)
        assertFalse(99 in read.waits)
    }

    @Test
    fun `brackets inside the text don't end the array early`() {
        val page = """
            <script>var latlngs = [{"name":"Golden: Wait is 5 mins","locationID":40,
            "infoWindow":"<p>[Lanes] 4<\/p><b>Hours of Operation<\/b>: 8am to 5:30pm (M-F),<br>8am to 1pm (Sat), closed Sunday and state holidays<br \/><b>Last updated<\/b>: 11/2/2026 2:14:00 PM<\/p>"}];</script>
        """.trimIndent()
        assertEquals(mapOf(40 to (WaitReading.Minutes(5) to denver("2026-11-02T14:14"))), read(page).waits)
    }
}
