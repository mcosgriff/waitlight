package io.cosgriff.waitlight.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import kotlin.math.roundToInt

class RankingTest {
    private fun denver(text: String): Instant = LocalDateTime.parse(text).atZone(AirCareHours.zone).toInstant()

    private val now = denver("2026-11-02T14:20")
    private val updated = denver("2026-11-02T14:14")

    /** Somewhere in Arvada, 80003. */
    private val arvada = GeoPoint(39.8277, -105.0656)

    /** A row whose drive time is [drive], by default 5 minutes plus a minute a mile, so the sums below are easy to check. */
    private fun row(
        name: String,
        reading: WaitReading,
        miles: Double?,
        at: Instant = updated,
        drive: Int? = miles?.let { (5 + it).roundToInt() },
    ) = StationRow(Stations.all.single { it.name == name }, reading, at, miles, drive)

    @Test
    fun `the catalog has the 18 stations with unique ids`() {
        assertEquals(18, Stations.all.size)
        assertEquals(18, Stations.all.map { it.id }.toSet().size)
        assertEquals(setOf("Sheridan", "Boulder", "Ken Caryl", "Fort Collins"), Stations.all.filter { it.lowProfileRims }.map { it.name }.toSet())
        assertEquals("Golden", Stations[40]?.name)
    }

    @Test
    fun `distances are straight-line miles`() {
        val arvadaStation = Stations[38]!!
        val golden = Stations[40]!!
        // About 5.5 miles apart as the crow flies.
        assertEquals(5.5, arvadaStation.location.milesTo(golden.location), 0.2)
        assertEquals(0.0, golden.location.milesTo(golden.location), 1e-9)
    }

    @Test
    fun `the map link pins the station's coordinates under its name, and the phone is its digits`() {
        val arvadaStation = Stations[38]!!
        assertEquals("geo:39.7899748,-105.0698573?q=39.7899748,-105.0698573(Arvada)", arvadaStation.mapUri)
        assertEquals("geo:39.6744566,-105.0014247?q=39.6744566,-105.0014247(Denver%20%28Lipan%29)", Stations[48]!!.mapUri)
        assertEquals("3034567090", arvadaStation.dialNumber)
        assertEquals("9702478378", Stations[51]!!.dialNumber)
    }

    @Test
    fun `total is drive plus wait, capped at 90, and none without an estimate`() {
        val drive = 15
        assertEquals(drive + 13, row("Arvada", WaitReading.Minutes(13), 10.0).totalMinutes)
        assertEquals(drive + 90, row("Parker", WaitReading.Minutes(120), 10.0).totalMinutes)
        assertEquals(drive + 90, row("Parker", WaitReading.MoreThan(60), 10.0).totalMinutes)
        assertNull(row("Longmont", WaitReading.NoEstimate, 10.0).totalMinutes)
        assertNull(row("Arvada", WaitReading.Minutes(13), null).totalMinutes)
        assertNull(row("Arvada", WaitReading.Minutes(13), 10.0, drive = null).totalMinutes)
    }

    @Test
    fun `a station with no drive time goes after the rest in fastest overall and is never the hero`() {
        val rows = listOf(
            row("Arvada", WaitReading.Minutes(30), 3.0), // 38
            row("Golden", WaitReading.Minutes(0), 2.0, drive = null), // no route
            row("Broomfield", WaitReading.Minutes(10), 6.0), // 21
        )
        assertEquals(listOf("Broomfield", "Arvada", "Golden"), names(arrange(rows, SortOrder.FASTEST_OVERALL, false, ListState.LIVE)))
        assertEquals("Broomfield", fastestOverall(rows, ListState.LIVE, now)?.station?.name)
    }

    @Test
    fun `nearest first`() {
        val rows = listOf(
            row("Golden", WaitReading.Minutes(8), 8.2),
            row("Arvada", WaitReading.Minutes(13), 3.1),
            row("Broomfield", WaitReading.Minutes(19), 6.4),
        )
        assertEquals(listOf("Arvada", "Broomfield", "Golden"), names(arrange(rows, SortOrder.NEAREST, false, ListState.LIVE)))
    }

    @Test
    fun `fastest overall, then stations without an estimate by distance`() {
        val rows = listOf(
            row("Arvada", WaitReading.Minutes(40), 3.1), // 8 + 40 = 48
            row("Golden", WaitReading.Minutes(8), 8.2), // 13 + 8 = 21
            row("Longmont", WaitReading.NoEstimate, 27.9),
            row("Broomfield", WaitReading.NoEstimate, 6.4),
            row("Boulder", WaitReading.Minutes(5), 17.0), // 22 + 5 = 27
        )
        assertEquals(
            listOf("Golden", "Boulder", "Arvada", "Broomfield", "Longmont"),
            names(arrange(rows, SortOrder.FASTEST_OVERALL, false, ListState.LIVE)),
        )
    }

    @Test
    fun `while closed, fastest overall falls back to nearest`() {
        val rows = listOf(row("Golden", WaitReading.Minutes(0), 8.2), row("Arvada", WaitReading.Minutes(50), 3.1))
        assertEquals(listOf("Arvada", "Golden"), names(arrange(rows, SortOrder.FASTEST_OVERALL, false, ListState.CLOSED)))
    }

    @Test
    fun `without a location the list is alphabetical`() {
        val rows = listOf(row("Sheridan", WaitReading.Minutes(8), null), row("Arvada", WaitReading.Minutes(13), null))
        assertEquals(listOf("Arvada", "Sheridan"), names(arrange(rows, SortOrder.FASTEST_OVERALL, false, ListState.LIVE)))
    }

    @Test
    fun `low-profile rims keeps only the four stations that test them`() {
        val rows = rowsFor(Stations.all.associate { it.id to (WaitReading.Minutes(10) as WaitReading to updated) }, arvada, DriveTable.EMPTY)
        val shown = arrange(rows, SortOrder.NEAREST, true, ListState.LIVE)
        assertEquals(4, shown.size)
        assertTrue(shown.all { it.station.lowProfileRims })
    }

    @Test
    fun `every station has a row, with or without a reading`() {
        val rows = rowsFor(mapOf(38 to (WaitReading.Minutes(10) to updated)), arvada, DriveTable.EMPTY)
        assertEquals(18, rows.size)
        assertEquals(WaitReading.Minutes(10), rows.single { it.station.id == 38 }.reading)
        val golden = rows.single { it.station.id == 40 }
        assertNull(golden.reading)
        assertNull(golden.totalMinutes)
        assertEquals(Badge.NoReading, golden.badge(now, WaitThresholds()))
        assertEquals(Badge.Closed, golden.badge(denver("2026-11-02T18:00"), WaitThresholds()))
        assertEquals(updated, newestUpdate(rows))
    }

    @Test
    fun `the hero is the least drive plus wait, skipping stale stations`() {
        val rows = listOf(
            row("Arvada", WaitReading.Minutes(13), 3.1), // 21
            row("Golden", WaitReading.Minutes(0), 8.2, at = denver("2026-11-02T13:13")), // 13, but an hour old
            row("Broomfield", WaitReading.Minutes(19), 6.4), // 30
        )
        assertEquals("Arvada", fastestOverall(rows, ListState.LIVE, now)?.station?.name)
    }

    @Test
    fun `a tie goes to the nearer station`() {
        val rows = listOf(row("Golden", WaitReading.Minutes(10), 8.0), row("Arvada", WaitReading.Minutes(15), 3.0))
        assertEquals("Arvada", fastestOverall(rows, ListState.DUE, now)?.station?.name)
    }

    @Test
    fun `no hero while closed, stale, offline or broken, or without a location`() {
        val rows = listOf(row("Arvada", WaitReading.Minutes(13), 3.1))
        for (state in listOf(ListState.CLOSED, ListState.STALE, ListState.OFFLINE, ListState.BROKEN)) {
            assertNull(fastestOverall(rows, state, now))
        }
        assertNull(fastestOverall(listOf(row("Arvada", WaitReading.Minutes(13), null)), ListState.LIVE, now))
        assertNull(fastestOverall(listOf(row("Longmont", WaitReading.NoEstimate, 3.0)), ListState.LIVE, now))
    }


    private fun names(rows: List<StationRow>) = rows.map { it.station.name }
}
