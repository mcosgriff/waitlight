package io.cosgriff.waitlight.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class VanDriveTableTest {
    /**
     * A 2 × 3 grid from 39.70, -105.10 in steps of 0.01 by 0.02; the south row spans columns 1–2, the north all
     * three. Each site's block is its 5 cells' minutes, south row first. 255 is no time.
     */
    private fun table(vararg sites: Pair<String, IntArray>, version: Int = 1, extra: Int = 0): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).apply {
            write("WLVD".toByteArray())
            writeByte(version)
            writeInt(39_700_000)
            writeInt(-105_100_000)
            writeInt(10_000)
            writeInt(20_000)
            writeInt(2)
            writeInt(3)
            writeInt(1); writeInt(2)
            writeInt(0); writeInt(3)
            writeInt(sites.size)
            for ((key, minutes) in sites) {
                val encoded = key.toByteArray()
                writeShort(encoded.size)
                write(encoded)
                minutes.forEach { writeByte(it) }
            }
            repeat(extra) { writeByte(0) }
        }
        return bytes.toByteArray()
    }

    private val sites = arrayOf(
        "a|harlan" to intArrayOf(10, 11, 20, 21, 22),
        "b|pecos" to intArrayOf(30, 255, 40, 41, 42),
    )

    @Test
    fun `reads only the sites asked for`() {
        val table = VanDriveTable.read(ByteArrayInputStream(table(*sites)), setOf("b|pecos", "c|not there"))
        assertEquals(setOf("b|pecos"), table.keys)
        assertNull(table.minutes(GeoPoint(39.70, -105.08), "a|harlan"))
        assertEquals(30, table.minutes(GeoPoint(39.70, -105.08), "b|pecos"))
    }

    @Test
    fun `a point takes its nearest cell, within the row's span`() {
        val table = VanDriveTable.read(ByteArrayInputStream(table(*sites)), setOf("a|harlan", "b|pecos"))
        assertEquals(11, table.minutes(GeoPoint(39.7004, -105.0590), "a|harlan"))
        assertEquals(22, table.minutes(GeoPoint(39.71, -105.06), "a|harlan"))
        assertEquals(20, table.minutes(GeoPoint(39.71, -105.10), "a|harlan"))
        // West of the south row's span, off the grid, and a cell with no route.
        assertNull(table.minutes(GeoPoint(39.70, -105.10), "a|harlan"))
        assertNull(table.minutes(GeoPoint(39.75, -105.08), "a|harlan"))
        assertNull(table.minutes(GeoPoint(39.70, -105.06), "b|pecos"))
    }

    @Test
    fun `a table that doesn't read throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            VanDriveTable.read(ByteArrayInputStream(table(*sites, version = 2)), emptySet())
        }
        assertThrows(IllegalArgumentException::class.java) {
            VanDriveTable.read(ByteArrayInputStream(table(*sites, extra = 1)), emptySet())
        }
        assertThrows(Exception::class.java) {
            VanDriveTable.read(ByteArrayInputStream(table(*sites).dropLast(2).toByteArray()), emptySet())
        }
    }

    @Test
    fun `the empty table has no times`() {
        assertNull(VanDriveTable.EMPTY.minutes(GeoPoint(39.7, -105.0), "a|harlan"))
    }
}
