package io.cosgriff.waitlight.domain

/** A Colorado ZIP code, its centroid and the place it's in ("Arvada"), from the bundled table (ADR-0005). */
data class ZipPlace(val zip: String, val point: GeoPoint, val place: String)

/** What a typed ZIP code is. */
sealed interface ZipEntry {
    /** A Colorado ZIP code in the table. */
    data class Found(val place: ZipPlace) : ZipEntry

    /** Not five digits. */
    data object Incomplete : ZipEntry

    /** Five digits, but not a Colorado ZIP code the table knows. */
    data object Unknown : ZipEntry
}

/**
 * A ZIP code's outline, from the Census's cartographic boundaries, simplified: its rings, outer ones and holes
 * alike. A point inside an odd number of them is in the ZIP.
 */
class ZipOutline(private val rings: List<Ring>) {
    /** One closed ring, in 1e-4 degrees ([UNITS] a degree), with its bounding box to skip most rings fast. */
    class Ring(private val lats: IntArray, private val lons: IntArray) {
        init {
            require(lats.size == lons.size && lats.size >= 3) { "A ring needs three points or more" }
        }

        private val minLat = lats.min()
        private val maxLat = lats.max()
        private val minLon = lons.min()
        private val maxLon = lons.max()

        /** Whether ([lat], [lon]), in 1e-4 degrees, is inside: a ray east from it crosses the ring an odd number of times. */
        fun contains(lat: Double, lon: Double): Boolean {
            if (lat < minLat || lat > maxLat || lon < minLon || lon > maxLon) return false
            var inside = false
            var j = lats.size - 1
            for (i in lats.indices) {
                val latI = lats[i].toDouble()
                val latJ = lats[j].toDouble()
                if ((latI > lat) != (latJ > lat)) {
                    val crossLon = lons[i] + (lons[j] - lons[i]) * (lat - latI) / (latJ - latI)
                    if (lon < crossLon) inside = !inside
                }
                j = i
            }
            return inside
        }
    }

    fun contains(point: GeoPoint): Boolean {
        val lat = point.latitude * UNITS
        val lon = point.longitude * UNITS
        return rings.count { it.contains(lat, lon) } % 2 == 1
    }

    companion object {
        const val UNITS = 10_000.0

        /**
         * An outline from its line's rings, as `scripts/colorado_zips.py` writes them: `lat,lon` pairs in 1e-4
         * degrees, space-separated, the first absolute and each after it the change from the one before.
         */
        fun parse(rings: List<String>): ZipOutline = ZipOutline(
            rings.map { ring ->
                val pairs = ring.trim().split(' ')
                val lats = IntArray(pairs.size)
                val lons = IntArray(pairs.size)
                pairs.forEachIndexed { i, pair ->
                    val parts = pair.split(',')
                    require(parts.size == 2) { "Bad point: $pair" }
                    val lat = requireNotNull(parts[0].toIntOrNull()) { "Bad latitude: $pair" }
                    val lon = requireNotNull(parts[1].toIntOrNull()) { "Bad longitude: $pair" }
                    lats[i] = if (i == 0) lat else lats[i - 1] + lat
                    lons[i] = if (i == 0) lon else lons[i - 1] + lon
                }
                Ring(lats, lons)
            },
        )
    }
}

/**
 * Every Colorado ZIP code with a Census area (ZCTA): its centroid and place name, and, once
 * [withOutlines] has added them, its outline. Made by `scripts/colorado_zips.py`; read with [parse].
 */
class ZipTable(places: Collection<ZipPlace>, private val outlines: Map<String, ZipOutline> = emptyMap()) {
    private val byZip: Map<String, ZipPlace> = places.associateBy { it.zip }

    init {
        require(byZip.keys.containsAll(outlines.keys)) { "Outlines for ZIPs not in the table: ${outlines.keys - byZip.keys}" }
    }

    val size: Int get() = byZip.size

    /** [zip] if it's five digits and in the table. */
    operator fun get(zip: String): ZipPlace? = if (isFiveDigits(zip)) byZip[zip] else null

    /** What [input] is, as typed. */
    fun check(input: String): ZipEntry = when {
        !isFiveDigits(input) -> ZipEntry.Incomplete
        else -> byZip[input]?.let { ZipEntry.Found(it) } ?: ZipEntry.Unknown
    }

    /**
     * The ZIP [point] is in, to name where the phone is: the one whose outline holds it (of two, where simplified
     * borders overlap, the one with the nearer centroid). Outside every outline, or without outlines, the [nearest].
     */
    fun placeOf(point: GeoPoint): ZipPlace? = outlines
        .filterValues { it.contains(point) }
        .keys
        .mapNotNull { byZip[it] }
        .minByOrNull { it.point.milesTo(point) }
        ?: nearest(point)

    /**
     * The ZIP whose centroid is nearest [point]. None farther than [NEAREST_MAX_MILES]: a phone outside Colorado
     * isn't in the nearest Colorado town.
     */
    fun nearest(point: GeoPoint): ZipPlace? = byZip.values
        .map { it to it.point.milesTo(point) }
        .minByOrNull { it.second }
        ?.takeIf { it.second <= NEAREST_MAX_MILES }
        ?.first

    companion object {
        /** Farther than this from every ZIP's centroid, the phone is in no ZIP the table knows. */
        const val NEAREST_MAX_MILES = 25.0

        val EMPTY = ZipTable(emptyList())

        /**
         * [table] with the outlines in [text]: `zip|ring|ring…` a line, `#` lines are comments ([ZipOutline.parse]).
         * A line that doesn't read, or an outline for a ZIP the table hasn't, throws: both are bundled, so that
         * is a build mistake.
         */
        fun withOutlines(table: ZipTable, text: String): ZipTable = ZipTable(
            table.byZip.values,
            text.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .associate { line ->
                    val parts = line.split('|')
                    require(parts.size >= 2 && isFiveDigits(parts[0])) { "Bad outline line: ${line.take(40)}" }
                    parts[0] to ZipOutline.parse(parts.drop(1))
                },
        )

        fun isFiveDigits(zip: String): Boolean = zip.length == 5 && zip.all { it in '0'..'9' }

        /**
         * The table from its text: `zip|latitude|longitude|place` a line, `#` lines are comments. A line that
         * doesn't read throws: the table is bundled, so that is a build mistake, never data to skip.
         */
        fun parse(text: String): ZipTable = ZipTable(
            text.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .map { line ->
                    val parts = line.split('|')
                    require(parts.size == 4 && isFiveDigits(parts[0]) && parts[3].isNotBlank()) { "Bad ZIP line: $line" }
                    val lat = requireNotNull(parts[1].toDoubleOrNull()) { "Bad latitude: $line" }
                    val lon = requireNotNull(parts[2].toDoubleOrNull()) { "Bad longitude: $line" }
                    ZipPlace(parts[0], GeoPoint(lat, lon), parts[3])
                }
                .toList(),
        )
    }
}

/**
 * Where distances are measured from, and what the list's header calls it. Location permission wins over a typed
 * ZIP code: the ZIP is used only while there's no fix ([here]).
 */
sealed interface Here {
    val point: GeoPoint

    /** The place the header names, or null for "Near you". */
    val place: ZipPlace?

    /** The phone's own location; [place] is the ZIP it's in ([ZipTable.placeOf]), if one is near. */
    data class Device(override val point: GeoPoint, override val place: ZipPlace?) : Here

    /** A ZIP code the user typed, at its centroid. */
    data class Zip(override val place: ZipPlace) : Here {
        override val point: GeoPoint get() = place.point
    }
}

/**
 * Where "here" is: the phone's [fix] when location is [permitted] and found; otherwise the saved [zip] if the
 * table has it; otherwise nowhere.
 */
fun here(permitted: Boolean, fix: GeoPoint?, zip: String?, table: ZipTable): Here? = when {
    permitted && fix != null -> Here.Device(fix, table.placeOf(fix))
    zip != null -> table[zip]?.let { Here.Zip(it) }
    else -> null
}
