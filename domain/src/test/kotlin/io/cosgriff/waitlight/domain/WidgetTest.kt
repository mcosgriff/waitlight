package io.cosgriff.waitlight.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime

class WidgetTest {
    private fun denver(text: String): Instant = LocalDateTime.parse(text).atZone(AirCareHours.zone).toInstant()

    // Monday 2026-11-02.
    private val now = denver("2026-11-02T14:20")
    private val updated = denver("2026-11-02T14:14")

    /** Somewhere in Arvada, 80003: Arvada, Broomfield and Golden are the nearest three. */
    private val arvada = GeoPoint(39.8277, -105.0656)

    private fun id(name: String) = Stations.all.single { it.name == name }.id

    private val readings = Stations.all.associate { it.id to (WaitReading.Minutes(13) as WaitReading to updated) }

    private fun content(
        at: Instant = now,
        waits: Map<Int, Pair<WaitReading, Instant>> = readings,
        here: GeoPoint? = arvada,
        outcome: FetchOutcome = FetchOutcome.OK,
    ) = widgetContent(WaitTimes(waits, outcome), here, at)

    private fun nearest(content: WidgetContent) = content as WidgetContent.Nearest

    @Test
    fun `shows the nearest three, nearest first`() {
        val rows = nearest(content()).rows
        assertEquals(listOf("Arvada", "Broomfield", "Golden"), rows.map { it.station.name })
        assertTrue(rows.zipWithNext().all { (a, b) -> a.distanceMiles!! <= b.distanceMiles!! })
        assertEquals(WIDGET_STATIONS, rows.size)
    }

    @Test
    fun `without a location asks for one, never an alphabetical guess`() {
        assertEquals(WidgetContent.NeedsLocation, content(here = null))
    }

    @Test
    fun `badges follow the list's rules`() {
        val waits = readings + mapOf(
            id("Arvada") to (WaitReading.Minutes(45) to updated),
            id("Broomfield") to (WaitReading.NoEstimate to updated),
        )
        val rows = nearest(content(waits = waits)).rows
        assertEquals(Badge.Live(WaitBand.MODERATE, ShownWait.Exact(45)), rows[0].badge)
        assertEquals(Badge.Open, rows[1].badge)
        assertEquals(Badge.Live(WaitBand.SHORT, ShownWait.Exact(13)), rows[2].badge)
    }

    @Test
    fun `a station goes stale on its own timestamp`() {
        val goldenUpdated = denver("2026-11-02T13:20")
        val waits = readings + (id("Golden") to (WaitReading.Minutes(8) to goldenUpdated))
        val rows = nearest(content(waits = waits)).rows
        assertEquals(Badge.Stale(WaitBand.SHORT, ShownWait.Exact(8), goldenUpdated), rows[2].badge)
        assertTrue(rows[0].badge is Badge.Live)
    }

    @Test
    fun `an unreadable page greys every badge`() {
        val rows = nearest(content(outcome = FetchOutcome.UNREADABLE)).rows
        assertTrue(rows.all { it.badge is Badge.Stale })
    }

    @Test
    fun `closed outside hours, and no reading before the first fetch`() {
        assertTrue(nearest(content(at = denver("2026-11-02T18:00"))).rows.all { it.badge == Badge.Closed })
        assertTrue(nearest(content(waits = emptyMap())).rows.all { it.badge == Badge.NoReading })
    }

    @Test
    fun `the age is the newest AirCare time of all the stations, not just the three shown`() {
        val newest = denver("2026-11-02T14:16")
        val waits = readings + (id("Parker") to (WaitReading.Minutes(5) to newest))
        assertEquals(newest, nearest(content(waits = waits)).newestUpdate)
        assertNull(nearest(content(waits = emptyMap())).newestUpdate)
    }

    @Test
    fun `redraws when the first shown badge goes stale`() {
        val goldenUpdated = denver("2026-11-02T14:05")
        val waits = readings + (id("Golden") to (WaitReading.Minutes(8) to goldenUpdated))
        val at = widgetRedrawAt(content(waits = waits), now)!!
        assertEquals(goldenUpdated.plus(Freshness.STALE_AFTER).plusSeconds(1), at)
        // Still fresh at exactly 30 minutes; stale just after.
        assertTrue(Freshness.isFresh(goldenUpdated, at.minusSeconds(1)))
        assertTrue(nearest(content(at = at, waits = waits)).rows[2].badge is Badge.Stale)
    }

    @Test
    fun `redraws at closing when nothing goes stale first`() {
        val late = denver("2026-11-02T17:20")
        val waits = Stations.all.associate { it.id to (WaitReading.Minutes(13) as WaitReading to denver("2026-11-02T17:14")) }
        assertEquals(denver("2026-11-02T17:30"), widgetRedrawAt(content(at = late, waits = waits), late))
    }

    @Test
    fun `stale badges don't call for a redraw of their own`() {
        val old = Stations.all.associate { it.id to (WaitReading.Minutes(13) as WaitReading to denver("2026-11-02T09:00")) }
        assertEquals(denver("2026-11-02T17:30"), widgetRedrawAt(content(waits = old), now))
    }

    @Test
    fun `no redraw while closed or without a location`() {
        val evening = denver("2026-11-02T18:00")
        assertNull(widgetRedrawAt(content(at = evening), evening))
        assertNull(widgetRedrawAt(WidgetContent.NeedsLocation, now))
    }

    @Test
    fun `a redraw is never in the past`() {
        val at = widgetRedrawAt(content(), now)!!
        assertTrue(Duration.between(now, at) > Duration.ZERO)
    }
}
