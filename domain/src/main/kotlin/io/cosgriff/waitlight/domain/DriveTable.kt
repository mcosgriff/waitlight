package io.cosgriff.waitlight.domain

import java.nio.ByteBuffer
import kotlin.math.roundToInt

/**
 * Drive minutes to each station from a grid over the gas emissions program area (about 250 m cells), routed on
 * OpenStreetMap's roads at development time (ADR-0007, `scripts/drive_times.py`). A point is looked up in the
 * cell whose centre is nearest. There is no traffic in it. Outside the program area, or in a cell with no road
 * near it, there is no time: never a guess from straight-line distance, which is off by enough to change the
 * fastest station.
 */
class DriveTable private constructor(
    private val south: Double,
    private val west: Double,
    private val latStep: Double,
    private val lonStep: Double,
    /** Each row's first column with times. */
    private val firstCol: IntArray,
    /** How many columns from [firstCol] each row has. */
    private val colCount: IntArray,
    /** Where each row's bytes start in [minutes]. */
    private val rowStart: IntArray,
    /** Station id to its byte's place in a cell. */
    private val slot: Map<Int, Int>,
    private val minutes: ByteArray,
) {
    /** Minutes from [from] to station [stationId], or null where the table has none. */
    fun minutes(from: GeoPoint, stationId: Int): Int? {
        val index = slot[stationId] ?: return null
        val row = ((from.latitude - south) / latStep).roundToInt()
        if (row !in firstCol.indices) return null
        val col = ((from.longitude - west) / lonStep).roundToInt() - firstCol[row]
        if (col !in 0 until colCount[row]) return null
        val value = minutes[rowStart[row] + col * slot.size + index].toInt() and 0xFF
        return value.takeUnless { it == NONE }
    }

    companion object {
        /** No time anywhere: what a table that can't be read is read as. */
        val EMPTY = DriveTable(0.0, 0.0, 1.0, 1.0, IntArray(0), IntArray(0), IntArray(0), emptyMap(), ByteArray(0))

        private const val VERSION = 2
        private const val NONE = 255
        private const val MICRO = 1e-6
        private val MAGIC = "WLDT".toByteArray(Charsets.US_ASCII)

        /** The table in the layout `scripts/drive_times.py` writes (its docstring has the layout). */
        fun parse(bytes: ByteArray): DriveTable {
            require(bytes.size > MAGIC.size && bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
                "Not a drive-time table"
            }
            val buffer = ByteBuffer.wrap(bytes, MAGIC.size, bytes.size - MAGIC.size)
            val version = buffer.get().toInt()
            require(version == VERSION) { "Unknown drive-time table version $version" }
            val south = buffer.int * MICRO
            val west = buffer.int * MICRO
            val latStep = buffer.int * MICRO
            val lonStep = buffer.int * MICRO
            val rows = buffer.int
            val cols = buffer.int
            val stations = buffer.int
            require(rows > 0 && cols > 0 && stations > 0 && latStep > 0 && lonStep > 0) { "Empty drive-time table" }
            val slot = (0 until stations).associateBy { buffer.int }
            val firstCol = IntArray(rows)
            val colCount = IntArray(rows)
            val rowStart = IntArray(rows)
            var total = 0L
            for (row in 0 until rows) {
                firstCol[row] = buffer.int
                colCount[row] = buffer.int
                require(firstCol[row] >= 0 && colCount[row] >= 0 && firstCol[row] + colCount[row] <= cols) {
                    "Bad span in row $row"
                }
                rowStart[row] = total.toInt()
                total += colCount[row].toLong() * stations
            }
            require(buffer.remaining().toLong() == total) {
                "Drive-time table has ${buffer.remaining()} bytes of minutes, expected $total"
            }
            val minutes = ByteArray(total.toInt()).also(buffer::get)
            return DriveTable(south, west, latStep, lonStep, firstCol, colCount, rowStart, slot, minutes)
        }
    }
}
