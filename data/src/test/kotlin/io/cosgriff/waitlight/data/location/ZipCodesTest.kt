package io.cosgriff.waitlight.data.location

import io.cosgriff.waitlight.domain.GeoPoint
import io.cosgriff.waitlight.domain.Stations
import io.cosgriff.waitlight.domain.ZipEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The bundled table as shipped (`src/main/assets`), and how it's loaded. */
class ZipCodesTest {
    private val asset = File("src/main/assets/${ZipCodes.ASSET}")

    private fun bundled() = ZipCodes({ asset.readText() }, Dispatchers.Unconfined)

    @Test
    fun `the bundled table has every Colorado ZIP with a Census area, and only Colorado's`() = runTest {
        val table = bundled().table()
        assertEquals(527, table.size)
        val zips = asset.readLines().filterNot { it.startsWith("#") }.map { it.substringBefore('|') }
        assertTrue(zips.all { it.take(3).toInt() in 800..816 })
        assertEquals(zips.sorted(), zips)
    }

    @Test
    fun `the bundled table names the places the design shows`() = runTest {
        val table = bundled().table()
        assertEquals("Arvada", table["80003"]?.place)
        assertEquals("Denver", table["80202"]?.place)
        assertEquals("Boulder", table["80301"]?.place)
        assertEquals("Cañon City", table["81212"]?.place)
        // A rural ZIP in no town takes its county.
        assertEquals("Park County", table["80456"]?.place)
        assertTrue(table.check("90210") is ZipEntry.Unknown)
    }

    @Test
    fun `every station is near a ZIP the table names`() = runTest {
        val table = bundled().table()
        for (station in Stations.all) {
            val nearest = table.nearest(station.location)
            assertTrue("${station.name} is near no ZIP", nearest != null && nearest.point.milesTo(station.location) < 5)
        }
        assertEquals("80003", table.nearest(GeoPoint(39.8277, -105.0656))?.zip)
    }

    @Test
    fun `the table is read once`() = runTest {
        var reads = 0
        val zips = ZipCodes({ reads++; "80003|39.8254|-105.0644|Arvada" }, Dispatchers.Unconfined)
        zips.table()
        zips.table()
        assertEquals(1, reads)
    }

    @Test
    fun `a table that can't be read is empty, not a crash`() = runTest {
        assertEquals(0, ZipCodes({ error("no asset") }, Dispatchers.Unconfined).table().size)
        assertEquals(0, ZipCodes({ "not|a|table" }, Dispatchers.Unconfined).table().size)
    }
}
