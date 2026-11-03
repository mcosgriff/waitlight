package io.cosgriff.waitlight.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime

class WaitsTest {
    private val thresholds = WaitThresholds()
    private fun denver(text: String): Instant = LocalDateTime.parse(text).atZone(AirCareHours.zone).toInstant()

    @Test
    fun `reads every wording seen on AirCare's page`() {
        assertEquals(WaitReading.Minutes(10), WaitText.parse("Arvada: Wait is 10 mins"))
        assertEquals(WaitReading.Minutes(0), WaitText.parse("Golden: Wait is 0 mins"))
        assertEquals(WaitReading.Minutes(1), WaitText.parse("Wait is 1 min"))
        assertEquals(WaitReading.NoEstimate, WaitText.parse("Longmont:  Station Open"))
        assertEquals(
            WaitReading.MoreThan(60),
            WaitText.parse(
                "Parker: The wait is more than 60 minutes. THIS TIME DOES NOT INCLUDE TIME IN LINE OUTSIDE OF THE TICKET GATE.",
            ),
        )
    }

    @Test
    fun `"Station Closed" is no reading, and isn't new wording`() {
        assertNull(WaitText.parse("Arvada: Station Closed"))
        assertTrue(WaitText.saysClosed("Arvada: Station Closed"))
        assertTrue(WaitText.saysClosed("Denver SE (Warren): Station Closed"))
        assertFalse(WaitText.saysClosed("Arvada: Wait is 10 mins"))
        assertFalse(WaitText.saysClosed("Longmont:  Station Open"))
    }

    @Test
    fun `new wording reads as nothing`() {
        assertNull(WaitText.parse("Arvada: Temporarily closed"))
        assertNull(WaitText.parse(""))
    }

    @Test
    fun `reads both timestamp formats in Denver time`() {
        assertEquals(denver("2026-11-02T16:14:15"), AirCareTimestamp.parse("11/2/2026 4:14:15 PM"))
        assertEquals(denver("2026-11-02T16:16"), AirCareTimestamp.parse("11/02/2026 04:16 PM"))
        assertEquals(denver("2026-11-02T08:05"), AirCareTimestamp.parse(" 11/2/2026 8:05 am "))
        assertNull(AirCareTimestamp.parse("yesterday"))
    }

    @Test
    fun `bands are inclusive at the low end`() {
        assertEquals(WaitBand.SHORT, WaitReading.Minutes(29).band(thresholds))
        assertEquals(WaitBand.MODERATE, WaitReading.Minutes(30).band(thresholds))
        assertEquals(WaitBand.MODERATE, WaitReading.Minutes(60).band(thresholds))
        assertEquals(WaitBand.LONG, WaitReading.Minutes(61).band(thresholds))
        assertEquals(WaitBand.LONG, WaitReading.MoreThan(60).band(thresholds))
        assertNull(WaitReading.NoEstimate.band(thresholds))
    }

    @Test
    fun `thresholds can be changed`() {
        val tight = WaitThresholds(greenMax = 15, yellowMax = 45)
        assertEquals(WaitBand.MODERATE, WaitReading.Minutes(20).band(tight))
        assertEquals(WaitBand.LONG, WaitReading.Minutes(46).band(tight))
        assertEquals(ShownWait.Over(45), WaitReading.Minutes(46).shown(tight))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `thresholds must be in order`() {
        WaitThresholds(greenMax = 60, yellowMax = 30)
    }

    @Test
    fun `a long wait shows as 60 plus`() {
        assertEquals(ShownWait.Exact(13), WaitReading.Minutes(13).shown(thresholds))
        assertEquals(ShownWait.Over(60), WaitReading.Minutes(75).shown(thresholds))
        assertEquals(ShownWait.Over(60), WaitReading.MoreThan(60).shown(thresholds))
        assertNull(WaitReading.NoEstimate.shown(thresholds))
    }

    @Test
    fun `badges while open and fresh`() {
        val now = denver("2026-11-02T14:20")
        val updated = denver("2026-11-02T14:14")
        assertEquals(Badge.Live(WaitBand.SHORT, ShownWait.Exact(13)), badgeFor(WaitReading.Minutes(13), updated, now, thresholds))
        assertEquals(Badge.Live(WaitBand.LONG, ShownWait.Over(60)), badgeFor(WaitReading.MoreThan(60), updated, now, thresholds))
        assertEquals(Badge.Open, badgeFor(WaitReading.NoEstimate, updated, now, thresholds))
    }

    @Test
    fun `a station's own old timestamp makes its badge stale`() {
        val now = denver("2026-11-02T16:20")
        // Golden on the live page: updated an hour before the others.
        val golden = denver("2026-11-02T15:13:36")
        assertEquals(Badge.Stale(WaitBand.SHORT, ShownWait.Exact(0), golden), badgeFor(WaitReading.Minutes(0), golden, now, thresholds))
    }

    @Test
    fun `exactly 30 minutes old is still fresh`() {
        val now = denver("2026-11-02T14:44")
        assertEquals(Badge.Live(WaitBand.SHORT, ShownWait.Exact(5)), badgeFor(WaitReading.Minutes(5), denver("2026-11-02T14:14"), now, thresholds))
    }

    @Test
    fun `unreadable data shows the last good reading as stale`() {
        val now = denver("2026-11-02T14:20")
        val updated = denver("2026-11-02T14:14")
        assertEquals(
            Badge.Stale(null, null, updated),
            badgeFor(WaitReading.NoEstimate, updated, now, thresholds, dataUsable = false),
        )
    }

    @Test
    fun `closed outside hours whatever the reading`() {
        val now = denver("2026-11-02T18:00")
        assertEquals(Badge.Closed, badgeFor(WaitReading.Minutes(13), denver("2026-11-02T17:29"), now, thresholds))
    }
}
