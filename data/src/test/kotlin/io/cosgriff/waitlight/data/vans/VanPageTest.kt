package io.cosgriff.waitlight.data.vans

import io.cosgriff.waitlight.domain.VanLiveStatus
import io.cosgriff.waitlight.domain.VanTabDays
import io.cosgriff.waitlight.domain.VanTabKind
import io.cosgriff.waitlight.domain.VanWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

/** AirCare's van page and its three tables as served on Friday 2026-11-20 at 5:27 PM (`src/test/resources/vans`). */
internal fun savedVanPage(): String = resource("/vans/van-locations-2026-11-20.html")

internal fun savedVanTable(day: Int): String = resource("/vans/van-table-day-$day-2026-11-20.html")

private fun resource(path: String) = checkNotNull(VanPageTest::class.java.getResource(path)).readText()

class VanPageTest {
    private val tabs = (VanPage.read(savedVanPage()) as VanPageRead.Read).tabs

    private fun joined() = tabs.map { VanPage.join(it, it.day?.let { day -> VanPage.rows(savedVanTable(day)) }) }

    @Test
    fun `four tabs, the last broken, with their tables' day ids from the iframes`() {
        assertEquals(
            listOf("M-Th (Current Week)", "Friday (Current Week)", "M-Th (Next Week)", "Friday (Next Week)"),
            tabs.map { it.title },
        )
        assertEquals(
            listOf(
                VanTabKind(VanWeek.CURRENT, VanTabDays.MON_THU),
                VanTabKind(VanWeek.CURRENT, VanTabDays.FRIDAY),
                VanTabKind(VanWeek.NEXT, VanTabDays.MON_THU),
                VanTabKind(VanWeek.NEXT, VanTabDays.FRIDAY),
            ),
            tabs.map { it.kind },
        )
        assertEquals(listOf(58, 60, 59, null), tabs.map { it.day })
        assertNull(tabs[3].pins)
        assertEquals("An unexpected error occurred.", tabs[3].problem)
    }

    @Test
    fun `25, 20 and 31 sites, every pin joined to its row`() {
        val read = joined()
        assertEquals(listOf(25, 20, 31, null), read.map { it.sites?.size })
        assertEquals(tabs.map { it.pins?.size }, read.map { it.sites?.size })
        assertNull(read[3].sites)
        assertEquals("An unexpected error occurred.", read[3].problem)
    }

    @Test
    fun `today's tab has live statuses, from the rows`() {
        val friday = joined()[1].sites!!
        val counts = friday.groupingBy { it.status }.eachCount()
        assertEquals(
            mapOf(
                VanLiveStatus.TESTING to 3,
                VanLiveStatus.UNKNOWN to 1,
                VanLiveStatus.NOT_TESTING to 1,
                VanLiveStatus.NOT_ON_SITE_UNSCHEDULED to 15,
            ),
            counts,
        )
        val harlan = friday.first { it.description == "ON-RAMP TO EB I-70 FROM HARLAN ST." }
        assertEquals(VanLiveStatus.TESTING, harlan.status)
        assertEquals("Arvada", harlan.city)
        assertEquals(setOf(DayOfWeek.FRIDAY), harlan.days)
        assertEquals(LocalTime.of(7, 0), harlan.opens)
        assertEquals(39.78313, harlan.point.latitude, 0.0)
    }

    @Test
    fun `the same site has the same key on every tab, whatever its locationID`() {
        val read = joined()
        val harlan = read.mapNotNull { tab -> tab.sites?.firstOrNull { "HARLAN" in it.description } }
        assertEquals(2, harlan.size)
        assertEquals(1, harlan.map { it.key }.toSet().size)
        assertEquals("39.78313,-105.05980|on-ramp to eb i-70 from harlan st", harlan.first().key)
    }

    @Test
    fun `a tab whose table couldn't be fetched has its sites without statuses`() {
        val sites = VanPage.join(tabs[1], null).sites!!
        assertEquals(20, sites.size)
        assertTrue(sites.all { it.status == null && it.city.isEmpty() })
    }

    @Test
    fun `a page without the map scripts can't be read`() {
        val changed = savedVanPage().replace("var latlngs", "var pins")
        assertTrue(VanPage.read(changed) is VanPageRead.Unreadable)
        assertTrue(VanPage.read("<html><body>Maintenance</body></html>") is VanPageRead.Unreadable)
    }

    @Test
    fun `table rows read through AirCare's sloppy markup`() {
        val rows = VanPage.rows(savedVanTable(60))
        assertEquals(20, rows.size)
        assertEquals(VanTableRow("Arvada", "On-site: Testing", "ON-RAMP TO EB I-70 FROM HARLAN ST. Fri 7:00 AM - 5:00 PM"), rows.first())
    }
}
