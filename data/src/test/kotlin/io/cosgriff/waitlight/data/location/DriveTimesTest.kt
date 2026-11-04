package io.cosgriff.waitlight.data.location

import io.cosgriff.waitlight.domain.DriveTable
import io.cosgriff.waitlight.domain.GeoPoint
import io.cosgriff.waitlight.domain.Stations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DriveTimesTest {
    private val asset = File("src/main/assets/${DriveTimes.ASSET}")
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private fun bundled() = DriveTimes({ asset.readBytes() }, scope, Dispatchers.Unconfined).table.value

    @Test
    fun `the bundled table routes every station from across the metro`() {
        val table = bundled()
        val places = listOf(
            GeoPoint(39.8254, -105.0644), // Arvada 80003
            GeoPoint(39.7392, -104.9903), // downtown Denver
            GeoPoint(40.0150, -105.2705), // Boulder
            GeoPoint(40.5853, -105.0844), // Fort Collins
            GeoPoint(39.3722, -104.8561), // Castle Rock
        )
        for (place in places) {
            for (station in Stations.all) {
                val minutes = table.minutes(place, station.id)
                assertNotNull("$place to ${station.name}", minutes)
                assertTrue("$place to ${station.name}: $minutes min", minutes!! in 1..180)
            }
        }
    }

    @Test
    fun `routed times are in line with the map`() {
        val table = bundled()
        val arvada = GeoPoint(39.8254, -105.0644)
        // A few minutes from Arvada 80003 to the Arvada station; Fort Collins is an hour or more up I-25.
        assertTrue(table.minutes(arvada, 38)!! <= 12)
        assertTrue(table.minutes(arvada, 51)!! >= 55)
        // Nearer the station is quicker.
        assertTrue(table.minutes(arvada, 38)!! < table.minutes(arvada, 50)!!)
    }

    @Test
    fun `no time outside the gas emissions program area`() {
        val table = bundled()
        assertNull(table.minutes(GeoPoint(38.8339, -104.8214), 38)) // Colorado Springs, off the grid
        assertNull(table.minutes(GeoPoint(40.3772, -105.5217), 38)) // Estes Park, on the grid, west of the area
        assertNull(table.minutes(GeoPoint(39.7114, -104.2269), 49)) // Byers, east of Kiowa Creek
        assertNull(table.minutes(GeoPoint(40.4233, -104.5000), 52)) // east of Greeley
    }

    @Test
    fun `a table that can't be read is empty, not a crash`() {
        assertSame(DriveTable.EMPTY, DriveTimes({ error("no asset") }, scope, Dispatchers.Unconfined).table.value)
        assertSame(DriveTable.EMPTY, DriveTimes({ ByteArray(3) }, scope, Dispatchers.Unconfined).table.value)
        assertEquals(DriveTable.EMPTY, DriveTimes({ "WLDT".toByteArray() }, scope, Dispatchers.Unconfined).table.value)
    }
}
