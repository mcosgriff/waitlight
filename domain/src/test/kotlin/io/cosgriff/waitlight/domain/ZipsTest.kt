package io.cosgriff.waitlight.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ZipsTest {
    private val table = ZipTable.parse(
        """
        # A comment, then three ZIPs as the bundled table has them.
        80003|39.8254|-105.0644|Arvada
        80202|39.7515|-104.9977|Denver

        80456|39.3104|-105.6895|Park County
        """.trimIndent(),
    )
    private val arvada = table["80003"]!!

    @Test
    fun `the table reads, skipping comments and blank lines`() {
        assertEquals(3, table.size)
        assertEquals(ZipPlace("80003", GeoPoint(39.8254, -105.0644), "Arvada"), arvada)
        assertEquals("Park County", table["80456"]?.place)
    }

    @Test
    fun `a line that doesn't read is a build mistake`() {
        assertThrows(IllegalArgumentException::class.java) { ZipTable.parse("80003|39.8|Arvada") }
        assertThrows(IllegalArgumentException::class.java) { ZipTable.parse("8003|39.8|-105.0|Arvada") }
        assertThrows(IllegalArgumentException::class.java) { ZipTable.parse("80003|north|-105.0|Arvada") }
        assertThrows(IllegalArgumentException::class.java) { ZipTable.parse("80003|39.8|-105.0| ") }
    }

    @Test
    fun `a typed ZIP is five digits and in the table`() {
        assertEquals(ZipEntry.Found(arvada), table.check("80003"))
        assertEquals(ZipEntry.Incomplete, table.check(""))
        assertEquals(ZipEntry.Incomplete, table.check("8000"))
        assertEquals(ZipEntry.Incomplete, table.check("800033"))
        assertEquals(ZipEntry.Incomplete, table.check("8000a"))
        assertEquals(ZipEntry.Incomplete, table.check(" 80003"))
        // Five digits, but not Colorado's (Beverly Hills) or not in the table.
        assertEquals(ZipEntry.Unknown, table.check("90210"))
        assertEquals(ZipEntry.Unknown, table.check("80004"))
        assertNull(table["90210"])
        assertNull(table["8000"])
    }

    @Test
    fun `the nearest ZIP names where a point is`() {
        // The Arvada station, 5185 Marshall St.
        assertEquals("80003", table.nearest(Stations[38]!!.location)?.zip)
        // Union Station, downtown Denver.
        assertEquals("80202", table.nearest(GeoPoint(39.7527, -105.0002))?.zip)
    }

    @Test
    fun `a point far from every ZIP is in none`() {
        // Cheyenne, Wyoming: about 90 miles from Arvada's centroid.
        assertNull(table.nearest(GeoPoint(41.14, -104.82)))
        assertNull(ZipTable.EMPTY.nearest(GeoPoint(39.8, -105.0)))
    }

    @Test
    fun `here is the fix when allowed and found, else the ZIP, else nowhere`() {
        val fix = GeoPoint(39.7527, -105.0002)
        // Location permission wins over a saved ZIP.
        assertEquals(Here.Device(fix, table["80202"]), here(permitted = true, fix = fix, zip = "80003", table = table))
        // Allowed, but no fix yet: the ZIP stands in.
        assertEquals(Here.Zip(arvada), here(permitted = true, fix = null, zip = "80003", table = table))
        // A fix left over from before the permission was taken back doesn't count.
        assertEquals(Here.Zip(arvada), here(permitted = false, fix = fix, zip = "80003", table = table))
        assertNull(here(permitted = false, fix = fix, zip = null, table = table))
        assertNull(here(permitted = true, fix = null, zip = null, table = table))
        // A saved ZIP the table no longer has is no ZIP.
        assertNull(here(permitted = false, fix = null, zip = "80004", table = table))
    }

    @Test
    fun `a ZIP is measured from its centroid`() {
        val zip = Here.Zip(arvada)
        assertEquals(arvada.point, zip.point)
        assertEquals(arvada, zip.place)
        assertNull(Here.Device(GeoPoint(41.14, -104.82), null).place)
    }

    // Two squares, in 1e-4 degrees, both 105.10 to 105.00 W: A from 39.80 to 39.90 N, with a hole from 39.84 to
    // 39.86 N, and B from 39.90 to 40.00 N. Each ring's first point is absolute, the rest changes from the last.
    // B's centroid is far north, so the south edge of B is nearer A's centroid than its own.
    private val squares = ZipTable(
        listOf(
            ZipPlace("80001", GeoPoint(39.82, -105.05), "A"),
            ZipPlace("80002", GeoPoint(39.99, -105.05), "B"),
            ZipPlace("80009", GeoPoint(39.70, -105.05), "C"),
        ),
    )
    private val outlined = ZipTable.withOutlines(
        squares,
        """
        # A comment.
        80001|398000,-1051000 0,1000 1000,0 0,-1000|398400,-1050600 0,200 200,0 0,-200
        80002|399000,-1051000 0,1000 1000,0 0,-1000
        """.trimIndent(),
    )

    @Test
    fun `the ZIP a point is in, not the one whose centroid is nearest`() {
        val southEdgeOfB = GeoPoint(39.902, -105.05) // 0.082° from A's centroid, 0.088° from B's
        assertEquals("80001", outlined.nearest(southEdgeOfB)?.zip)
        assertEquals("80002", outlined.placeOf(southEdgeOfB)?.zip)
        assertEquals("80001", outlined.placeOf(GeoPoint(39.89, -105.05))?.zip)
    }

    @Test
    fun `a hole isn't in the ZIP, and outside every outline the nearest centroid names it`() {
        // In A's hole: in no outline, so the nearest centroid, A's.
        assertEquals("80001", outlined.placeOf(GeoPoint(39.85, -105.05))?.zip)
        // South of both squares: C's centroid.
        assertEquals("80009", outlined.placeOf(GeoPoint(39.72, -105.05))?.zip)
        // Without outlines, placeOf is nearest.
        assertEquals("80001", squares.placeOf(GeoPoint(39.902, -105.05))?.zip)
        // Far from everything, nothing.
        assertNull(outlined.placeOf(GeoPoint(41.14, -104.82)))
    }

    @Test
    fun `here names the ZIP the fix is in`() {
        val fix = GeoPoint(39.902, -105.05)
        assertEquals(Here.Device(fix, outlined["80002"]), here(permitted = true, fix = fix, zip = null, table = outlined))
    }

    @Test
    fun `outlines that don't read are a build mistake`() {
        assertThrows(IllegalArgumentException::class.java) { ZipTable.withOutlines(squares, "80001") }
        assertThrows(IllegalArgumentException::class.java) { ZipTable.withOutlines(squares, "80001|398000,-1051000 0,1000") }
        assertThrows(IllegalArgumentException::class.java) { ZipTable.withOutlines(squares, "80001|398000 0,1000 1000,0") }
        // An outline for a ZIP the table hasn't.
        assertThrows(IllegalArgumentException::class.java) {
            ZipTable.withOutlines(squares, "80777|398000,-1051000 0,1000 1000,0 0,-1000")
        }
    }
}
