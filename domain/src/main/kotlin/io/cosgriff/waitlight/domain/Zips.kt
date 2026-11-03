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
 * Every Colorado ZIP code with a Census area (ZCTA): its centroid and place name. Made by
 * `scripts/colorado_zips.py`; read with [parse].
 */
class ZipTable(places: Collection<ZipPlace>) {
    private val byZip: Map<String, ZipPlace> = places.associateBy { it.zip }

    val size: Int get() = byZip.size

    /** [zip] if it's five digits and in the table. */
    operator fun get(zip: String): ZipPlace? = if (isFiveDigits(zip)) byZip[zip] else null

    /** What [input] is, as typed. */
    fun check(input: String): ZipEntry = when {
        !isFiveDigits(input) -> ZipEntry.Incomplete
        else -> byZip[input]?.let { ZipEntry.Found(it) } ?: ZipEntry.Unknown
    }

    /**
     * The ZIP whose centroid is nearest [point], to name where the phone is. None farther than [NEAREST_MAX_MILES]:
     * a phone outside Colorado isn't in the nearest Colorado town.
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

    /** The phone's own location; [place] is the nearest ZIP's, if one is near. */
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
    permitted && fix != null -> Here.Device(fix, table.nearest(fix))
    zip != null -> table[zip]?.let { Here.Zip(it) }
    else -> null
}
