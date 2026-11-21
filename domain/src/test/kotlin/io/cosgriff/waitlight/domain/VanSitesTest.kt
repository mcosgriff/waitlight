package io.cosgriff.waitlight.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalTime

/** Site texts as AirCare's van page had them on 2026-11-20. */
class VanSitesTest {
    @Test
    fun `days and hours come off the end of the text`() {
        val text = VanSiteText.parse("ON-RAMP TO EB I-70 FROM HARLAN ST.    Mon Tues Wed Thur   7:00 AM - 5:00 PM")!!
        assertEquals("ON-RAMP TO EB I-70 FROM HARLAN ST.", text.description)
        assertEquals(setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY), text.days)
        assertEquals(LocalTime.of(7, 0), text.opens)
        assertEquals(LocalTime.of(17, 0), text.closes)
    }

    @Test
    fun `one day, odd minutes and an exit number`() {
        val havana = VanSiteText.parse("ON-RAMP TO EB I-70 from HAVANA ST.    Mon   6:00 AM - 2:30 PM")!!
        assertEquals(setOf(MONDAY), havana.days)
        assertEquals(LocalTime.of(14, 30), havana.closes)
        val airport = VanSiteText.parse("ON-RAMP to WB I-70 from AIRPORT blvd    Tues Wed Thur   6:35 AM - 2:00 PM")!!
        assertEquals(LocalTime.of(6, 35), airport.opens)
        val founders = VanSiteText.parse("ON-RAMP to NB I-25 from FOUNDERS PKWY (Exit 184)       Mon Tues Wed Thur   9:00 AM - 3:30 PM")!!
        assertEquals("ON-RAMP to NB I-25 from FOUNDERS PKWY (Exit 184)", founders.description)
        val friday = VanSiteText.parse("EB SH-192 (Carpenter Rd) after intersection with CR-9    Fri   7:00 AM - 5:30 PM")!!
        assertEquals(setOf(FRIDAY), friday.days)
        assertEquals(LocalTime.of(12, 0), VanSiteText.parse("EB HILLTOP RD after MERRYVALE TRAIL    Mon Tues Wed   7:00 AM - 12:00 PM")!!.closes)
    }

    @Test
    fun `text without days and hours doesn't read`() {
        assertNull(VanSiteText.parse("ON-RAMP TO EB I-70 FROM HARLAN ST."))
        assertNull(VanSiteText.parse("ON-RAMP TO EB I-70 FROM HARLAN ST.  Sat  7:00 AM - 5:00 PM"))
        assertNull(VanSiteText.parse("ON-RAMP TO EB I-70 FROM HARLAN ST.  Mon  5:00 PM - 7:00 AM"))
    }

    @Test
    fun `ramps read from the cross street onto the highway`() {
        assertEquals(VanName.Ramp("Harlan St", "EB I-70"), VanName.of("ON-RAMP TO EB I-70 FROM HARLAN ST."))
        assertEquals(VanName.Ramp("Pecos St", "WB I-70"), VanName.of("ON-RAMP TO WB I-70 from PECOS ST."))
        assertEquals(VanName.Ramp("Ken Caryl Ave", "EB C-470"), VanName.of("ON-RAMP TO EB C-470 from KEN CARYL AVE."))
        assertEquals(VanName.Ramp("27th St", "WB US-34"), VanName.of("ON-RAMP TO WB US-34 from 27TH ST"))
        assertEquals(VanName.Ramp("Green Valley Ranch Blvd", "SB Peña"), VanName.of("ON-RAMP to SB PENA from GREEN VALLEY RANCH Blvd"))
        assertEquals(VanName.Ramp("Sheridan Blvd", "SB US-285 (Hampden Ave)"), VanName.of("ON-RAMP to SB US-285 (HAMPDEN ave) from SHERIDAN blvd"))
        assertEquals(VanName.Ramp("SH-257 (CR 21)", "WB US-34"), VanName.of("ON-RAMP to WB US-34 from SH-257 (CR 21)"))
    }

    @Test
    fun `an exit number stays, with the highway`() {
        assertEquals(VanName.Ramp("Founders Pkwy", "NB I-25 (Exit 184)"), VanName.of("ON-RAMP to NB I-25 from FOUNDERS PKWY (Exit 184)"))
    }

    @Test
    fun `two units is a flag, not part of the name`() {
        val text = "ON-RAMP to WB I-70 from NB I-25 (two units at site)"
        assertEquals(VanName.Ramp("NB I-25", "WB I-70"), VanName.of(text))
        assertTrue(VanName.hasTwoUnits(text))
        assertTrue(VanName.hasTwoUnits("ON-RAMP TO SB I-25 from EB COUNTY LINE RD. (two units on site)"))
        assertFalse(VanName.hasTwoUnits("ON-RAMP TO WB I-70 from NB I-25"))
    }

    @Test
    fun `a ramp worded without ON-RAMP`() {
        assertEquals(VanName.Ramp("CO-257", "EB US-34 Business"), VanName.of("EB US-34 BUSINESS from CO-257"))
        assertEquals(VanName.Ramp("WB 287", "SB Wadsworth"), VanName.of("SB WADSWORTH from WB 287"))
    }

    @Test
    fun `surface-road sites read past the cross street`() {
        assertEquals(VanName.Road("NB Taft Hill Rd", "Harmony Rd"), VanName.of("NB TAFT HILL RD. after HARMONY RD."))
        assertEquals(VanName.Road("EB SH-192 (Carpenter Rd)", "CR-9"), VanName.of("EB SH-192 (Carpenter Rd) after intersection with CR-9"))
        assertEquals(VanName.Road("EB Trilby Rd", "Shields"), VanName.of("EB Trilby Rd just after Shields"))
    }

    @Test
    fun `anything else is tidied as it is`() {
        assertEquals(VanName.Plain("Somewhere New"), VanName.of("SOMEWHERE NEW"))
    }

    @Test
    fun `statuses in AirCare's words`() {
        assertEquals(VanLiveStatus.TESTING, VanLiveStatus.parse("On-site: Testing"))
        assertEquals(VanLiveStatus.NOT_TESTING, VanLiveStatus.parse("On-site: Not Testing"))
        assertEquals(VanLiveStatus.UNKNOWN, VanLiveStatus.parse("On-site:  Status Unknown"))
        assertEquals(VanLiveStatus.NOT_ON_SITE_SCHEDULED, VanLiveStatus.parse("Not On-site: Currently Scheduled"))
        assertEquals(VanLiveStatus.NOT_ON_SITE_UNSCHEDULED, VanLiveStatus.parse("Not On-site: Not Currently Scheduled"))
        assertNull(VanLiveStatus.parse("Testing soon"))
    }

    @Test
    fun `the pin's icon cross-checks the status`() {
        assertTrue(VanLiveStatus.agrees(VanLiveStatus.TESTING, "icon9"))
        assertTrue(VanLiveStatus.agrees(VanLiveStatus.NOT_TESTING, "icon10"))
        assertTrue(VanLiveStatus.agrees(VanLiveStatus.UNKNOWN, "icon11"))
        assertTrue(VanLiveStatus.agrees(VanLiveStatus.NOT_ON_SITE_SCHEDULED, "icon7"))
        assertTrue(VanLiveStatus.agrees(VanLiveStatus.NOT_ON_SITE_UNSCHEDULED, "icon7"))
        assertFalse(VanLiveStatus.agrees(VanLiveStatus.TESTING, "icon7"))
    }

    @Test
    fun `a site's key is its rounded coordinates and normalised text, the same on every tab`() {
        val point = GeoPoint(40.47873, -105.023)
        val key = VanSite.siteKey(point, "EB SH-192 (Carpenter Rd)  after intersection with CR-9.")
        assertEquals("40.47873,-105.02300|eb sh-192 (carpenter rd) after intersection with cr-9", key)
        assertEquals(key, VanSite.siteKey(point, " eb sh-192 (Carpenter Rd) after intersection with CR-9"))
        assertEquals("39.12346,-105.00000|x", VanSite.siteKey(GeoPoint(39.123455, -105.0), "X"))
    }

    @Test
    fun `tab titles`() {
        assertEquals(VanTabKind(VanWeek.CURRENT, VanTabDays.MON_THU), VanTabKind.parse("M-Th (Current Week)"))
        assertEquals(VanTabKind(VanWeek.CURRENT, VanTabDays.FRIDAY), VanTabKind.parse("Friday (Current Week)"))
        assertEquals(VanTabKind(VanWeek.NEXT, VanTabDays.MON_THU), VanTabKind.parse("  M-Th (Next Week) "))
        assertEquals(VanTabKind(VanWeek.NEXT, VanTabDays.FRIDAY), VanTabKind.parse("Friday (Next Week)"))
        assertNull(VanTabKind.parse("Saturday"))
    }

    @Test
    fun `a geo link labelled for the map app`() {
        val site = VanSite("x", GeoPoint(39.78313, -105.0598), setOf(FRIDAY), LocalTime.of(7, 0), LocalTime.of(17, 0), "Arvada", null)
        assertEquals("geo:39.78313,-105.0598?q=39.78313,-105.0598(Harlan%20St%20to%20EB%20I-70)", site.mapUri("Harlan St to EB I-70"))
    }
}
