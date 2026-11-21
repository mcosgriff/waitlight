package io.cosgriff.waitlight.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime

/** The list header's decisions, Monday 2026-11-02 in Denver. */
class ListHeaderTest {
    private fun denver(text: String): Instant = LocalDateTime.parse(text).atZone(AirCareHours.zone).toInstant()

    private val arvada = ZipPlace("80003", GeoPoint(39.8254, -105.0644), "Arvada")
    private val atArvada = Here.Device(GeoPoint(39.83, -105.06), arvada)

    private fun header(
        now: Instant,
        newest: Instant? = denver("2026-11-02T14:14"),
        outcome: FetchOutcome = FetchOutcome.OK,
        here: Here? = atArvada,
        allowed: Boolean = true,
        order: SortOrder = SortOrder.NEAREST,
    ): ListHeader {
        val state = listState(now, newest, outcome)
        return listHeader(state, now, newest, refreshButton(state, now, newest), here, allowed, order)
    }

    @Test
    fun `the place line`() {
        assertEquals(PlaceLine.Device(arvada), placeLine(atArvada, true))
        assertEquals(PlaceLine.NearYou, placeLine(Here.Device(GeoPoint(41.1, -104.8), null), true))
        assertEquals(PlaceLine.Zip(arvada), placeLine(Here.Zip(arvada), false))
        assertEquals(PlaceLine.Finding, placeLine(null, true))
        assertEquals(PlaceLine.LocationOff, placeLine(null, false))
    }

    @Test
    fun `live - minutes ago and the next update`() {
        val live = header(denver("2026-11-02T14:20"))
        assertEquals(UpdatedLine.MinutesAgo(6), live.updated)
        assertEquals(NextLine.NextUpdateIn(9), live.next)
        assertNull(live.banner)
        assertNull(live.opening)
        assertEquals(UpdatedLine.MinutesAgo(0), header(denver("2026-11-02T14:14:30")).updated)
    }

    @Test
    fun `due - new data, or getting the first`() {
        assertEquals(NextLine.NewData, header(denver("2026-11-02T14:30")).next)
        val first = header(denver("2026-11-02T14:30"), newest = null)
        assertEquals(UpdatedLine.Getting, first.updated)
        assertNull(first.next)
    }

    @Test
    fun `stale, offline and broken - the last time, its age and the banner`() {
        val stale = header(denver("2026-11-02T15:00"), outcome = FetchOutcome.FAILED)
        assertEquals(UpdatedLine.LastUpdated(denver("2026-11-02T14:14")), stale.updated)
        assertEquals(NextLine.Age(Duration.ofMinutes(46)), stale.next)
        assertEquals(ListBanner.STALE, stale.banner)
        assertEquals(ListBanner.OFFLINE, header(denver("2026-11-02T15:00"), outcome = FetchOutcome.OFFLINE).banner)
        val broken = header(denver("2026-11-02T14:20"), outcome = FetchOutcome.UNREADABLE)
        assertEquals(ListBanner.BROKEN, broken.banner)
        assertEquals(NextLine.Unavailable, broken.next)
        assertEquals(UpdatedLine.NoneYet, header(denver("2026-11-02T14:20"), newest = null, outcome = FetchOutcome.UNREADABLE).updated)
    }

    @Test
    fun `closed - since closing, opening next, and nearest chosen whatever the chip`() {
        val closed = header(denver("2026-11-02T18:00"), order = SortOrder.FASTEST_OVERALL)
        assertEquals(UpdatedLine.ClosedSince(LocalDateTime.parse("2026-11-02T17:30").atZone(AirCareHours.zone)), closed.updated)
        assertEquals(NextLine.WaitsReturn, closed.next)
        assertEquals(Opening(LocalDateTime.parse("2026-11-03T08:00").atZone(AirCareHours.zone), OpeningDay.TOMORROW), closed.opening)
        assertEquals(SortChips(shown = true, nearestSelected = true, fastestSelected = false), closed.chips)
    }

    @Test
    fun `sort chips - as chosen while open, hidden without a location`() {
        assertEquals(SortChips(true, nearestSelected = false, fastestSelected = true), header(denver("2026-11-02T14:20"), order = SortOrder.FASTEST_OVERALL).chips)
        assertEquals(false, header(denver("2026-11-02T14:20"), here = null).chips.shown)
    }
}
