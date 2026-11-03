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
}
