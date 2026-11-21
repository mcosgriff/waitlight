package io.cosgriff.waitlight.domain

import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import kotlin.math.roundToInt

/**
 * Drive minutes to known van sites from the station table's grid (ADR-0007), routed on OpenStreetMap's roads at
 * development time (ADR-0008, `scripts/van_drive_times.py`). The file is site-major, so [read] keeps only the
 * sites asked for: the twenty or so out on the days the list shows, not the whole catalog. A site not in the table
 * has no time, never a straight-line guess.
 */
class VanDriveTable private constructor(
    private val south: Double,
    private val west: Double,
    private val latStep: Double,
    private val lonStep: Double,
    private val firstCol: IntArray,
    private val colCount: IntArray,
    /** Where each row starts in a site's block. */
    private val rowStart: IntArray,
    private val blocks: Map<String, ByteArray>,
) {
    /** The site keys the table read. */
    val keys: Set<String> get() = blocks.keys

    /** Minutes from [from] to the site with [key] ([VanSite.key]), or null where the table has none. */
    fun minutes(from: GeoPoint, key: String): Int? {
        val block = blocks[key] ?: return null
        val row = ((from.latitude - south) / latStep).roundToInt()
        if (row !in firstCol.indices) return null
        val col = ((from.longitude - west) / lonStep).roundToInt() - firstCol[row]
        if (col !in 0 until colCount[row]) return null
        return (block[rowStart[row] + col].toInt() and 0xFF).takeUnless { it == NONE }
    }

    companion object {
        val EMPTY = VanDriveTable(0.0, 0.0, 1.0, 1.0, IntArray(0), IntArray(0), IntArray(0), emptyMap())

        private const val VERSION = 1
        private const val NONE = 255
        private const val MICRO = 1e-6
        private val MAGIC = "WLVD".toByteArray(Charsets.US_ASCII)

        /**
         * The sites in [wanted] from a table in the layout `scripts/van_drive_times.py` writes (its docstring has
         * it); the rest are skipped unread. Throws on a table that doesn't read: it's bundled, so that's a build
         * mistake.
         */
        fun read(input: InputStream, wanted: Set<String>): VanDriveTable {
            val data = DataInputStream(input.buffered())
            val magic = ByteArray(MAGIC.size).also(data::readFully)
            require(magic.contentEquals(MAGIC)) { "Not a van drive-time table" }
            val version = data.readUnsignedByte()
            require(version == VERSION) { "Unknown van drive-time table version $version" }
            val south = data.readInt() * MICRO
            val west = data.readInt() * MICRO
            val latStep = data.readInt() * MICRO
            val lonStep = data.readInt() * MICRO
            val rows = data.readInt()
            val cols = data.readInt()
            require(rows > 0 && cols > 0 && latStep > 0 && lonStep > 0) { "Empty van drive-time table" }
            val firstCol = IntArray(rows)
            val colCount = IntArray(rows)
            val rowStart = IntArray(rows)
            var cells = 0
            for (row in 0 until rows) {
                firstCol[row] = data.readInt()
                colCount[row] = data.readInt()
                require(firstCol[row] >= 0 && colCount[row] >= 0 && firstCol[row] + colCount[row] <= cols) {
                    "Bad span in row $row"
                }
                rowStart[row] = cells
                cells += colCount[row]
            }
            val sites = data.readInt()
            require(sites >= 0) { "Bad site count $sites" }
            val blocks = HashMap<String, ByteArray>()
            repeat(sites) {
                val key = ByteArray(data.readUnsignedShort()).also(data::readFully).toString(Charsets.UTF_8)
                if (key in wanted) {
                    blocks[key] = ByteArray(cells).also(data::readFully)
                } else {
                    skipFully(data, cells.toLong())
                }
            }
            require(data.read() == -1) { "Van drive-time table has bytes past its last site" }
            return VanDriveTable(south, west, latStep, lonStep, firstCol, colCount, rowStart, blocks)
        }

        private fun skipFully(input: DataInputStream, count: Long) {
            var left = count
            while (left > 0) {
                val skipped = input.skip(left)
                if (skipped <= 0) {
                    if (input.read() == -1) throw EOFException("Van drive-time table ends inside a site")
                    left--
                } else {
                    left -= skipped
                }
            }
        }
    }
}
