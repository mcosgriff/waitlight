package io.cosgriff.waitlight.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class DriveTableTest {
    /**
     * A 2 × 3 grid from 39.70, -105.10 in steps of 0.01 by 0.02, for Arvada (38) and Golden (40), each row spanning
     * all three columns unless [spans] says otherwise; minutes go row by row from the south-west, Arvada then
     * Golden in each cell. 255 is no time.
     */
    private fun table(
        vararg minutes: Int,
        stations: IntArray = intArrayOf(38, 40),
        spans: List<Pair<Int, Int>> = listOf(0 to 3, 0 to 3),
        version: Int = 2,
    ): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).apply {
            write("WLDT".toByteArray())
            writeByte(version)
            writeInt(39_700_000)
            writeInt(-105_100_000)
            writeInt(10_000)
            writeInt(20_000)
            writeInt(2)
            writeInt(3)
            writeInt(stations.size)
            stations.forEach(::writeInt)
            spans.forEach { (first, count) -> writeInt(first); writeInt(count) }
            minutes.forEach(::writeByte)
        }
        return bytes.toByteArray()
    }

    private val sample = DriveTable.parse(
        table(
            // South row: three cells west to east.
            10, 20, 11, 21, 255, 255,
            // North row.
            12, 22, 13, 23, 200, 254,
        ),
    )

    @Test
    fun `a point takes its nearest cell's minutes for the station`() {
        assertEquals(10, sample.minutes(GeoPoint(39.70, -105.10), 38))
        assertEquals(20, sample.minutes(GeoPoint(39.70, -105.10), 40))
        // Nearer the second column's centre than the first's.
        assertEquals(11, sample.minutes(GeoPoint(39.702, -105.089), 38))
        assertEquals(23, sample.minutes(GeoPoint(39.708, -105.08), 40))
        assertEquals(254, sample.minutes(GeoPoint(39.71, -105.06), 40))
    }

    @Test
    fun `no time outside the grid, in a cell without one, or for a station not in it`() {
        assertNull(sample.minutes(GeoPoint(39.69, -105.10), 38))
        assertNull(sample.minutes(GeoPoint(39.72, -105.10), 38))
        assertNull(sample.minutes(GeoPoint(39.70, -105.12), 38))
        assertNull(sample.minutes(GeoPoint(39.70, -105.04), 38))
        assertNull(sample.minutes(GeoPoint(39.70, -105.06), 38))
        assertNull(sample.minutes(GeoPoint(39.70, -105.10), 51))
        assertNull(DriveTable.EMPTY.minutes(GeoPoint(39.70, -105.10), 38))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a file that isn't a table is refused`() {
        DriveTable.parse("not a table".toByteArray())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a newer version is refused`() {
        DriveTable.parse(table(*IntArray(12), version = 3))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a short table is refused`() {
        DriveTable.parse(table(*IntArray(11)))
    }

    @Test
    fun `a row keeps only its span, and there is no time either side of it`() {
        // South row: just the middle column; north row: none.
        val spanned = DriveTable.parse(table(11, 21, spans = listOf(1 to 1, 0 to 0)))
        assertEquals(11, spanned.minutes(GeoPoint(39.70, -105.08), 38))
        assertEquals(21, spanned.minutes(GeoPoint(39.70, -105.08), 40))
        assertNull(spanned.minutes(GeoPoint(39.70, -105.10), 38))
        assertNull(spanned.minutes(GeoPoint(39.70, -105.06), 38))
        assertNull(spanned.minutes(GeoPoint(39.71, -105.08), 38))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a span past the row's end is refused`() {
        DriveTable.parse(table(*IntArray(8), spans = listOf(1 to 3, 0 to 1)))
    }

    @Test
    fun `rows take their drive time from the table, and none without a location`() {
        val here = GeoPoint(39.70, -105.10)
        val rows = rowsFor(emptyMap(), here, sample).associateBy { it.station.id }
        assertEquals(10, rows.getValue(38).driveMinutes)
        assertEquals(20, rows.getValue(40).driveMinutes)
        assertNull(rows.getValue(51).driveMinutes)
        assertNull(rowsFor(emptyMap(), null, sample).first().driveMinutes)
    }
}
