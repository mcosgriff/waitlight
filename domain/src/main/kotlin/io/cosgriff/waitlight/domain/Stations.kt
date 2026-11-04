package io.cosgriff.waitlight.domain

import java.net.URLEncoder
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** A point on the map, in degrees. */
data class GeoPoint(val latitude: Double, val longitude: Double) {
    /** Straight-line (great-circle) distance in miles. Used for sorting by nearest. */
    fun milesTo(other: GeoPoint): Double {
        val lat1 = Math.toRadians(latitude)
        val lat2 = Math.toRadians(other.latitude)
        val dLat = lat2 - lat1
        val dLng = Math.toRadians(other.longitude - longitude)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_MILES * asin(sqrt(h))
    }

    private companion object {
        const val EARTH_RADIUS_MILES = 3958.8
    }
}

/**
 * An AirCare Colorado station: what stays the same from one fetch to the next. [id] is AirCare's own
 * `locationID`, which is how a fetched wait is matched to its station; names can change their wording.
 */
data class Station(
    val id: Int,
    val name: String,
    val street: String,
    val city: String,
    val zip: String,
    val location: GeoPoint,
    val phone: String,
    val lanes: Int,
    /** Has the dynamometer for low-profile rims (17″ and up) and wheels over 34″. */
    val lowProfileRims: Boolean,
) {
    val address: String get() = "$street, $city, CO $zip"

    /**
     * A `geo:` link to the station, pinned at its coordinates and labelled with its name, for whichever map app
     * the user picks (Google Maps, CoMaps, OsmAnd…) to give directions from.
     */
    val mapUri: String
        get() {
            val at = "${location.latitude},${location.longitude}"
            return "geo:$at?q=$at(" + URLEncoder.encode(name, Charsets.UTF_8).replace("+", "%20") + ")"
        }

    /** The number to dial, digits only. */
    val dialNumber: String get() = phone.filter { it.isDigit() }
}

/**
 * The 18 stations, as AirCare's wait-times page listed them on 2026-11-02 (names, addresses, phones, lanes and
 * the page's own coordinates). The page is the source: when it disagrees, the fetch logs it and this list is
 * updated by hand. No diesel testing at any of them.
 */
object Stations {
    private const val DENVER_PHONE = "303-456-7090"
    private const val NORTH_PHONE = "970-247-8378"

    val all: List<Station> = listOf(
        Station(38, "Arvada", "5185 Marshall St.", "Arvada", "80002", GeoPoint(39.7899748, -105.0698573), DENVER_PHONE, 7, false),
        Station(49, "Aurora", "12042 E. 30th Ave.", "Aurora", "80010", GeoPoint(39.7586182, -104.8483314), DENVER_PHONE, 6, false),
        Station(46, "Boulder", "5655 Airport Blvd.", "Boulder", "80301", GeoPoint(40.0377353, -105.2233758), DENVER_PHONE, 5, true),
        Station(42, "Broomfield", "11609 Teller St.", "Broomfield", "80020", GeoPoint(39.9083993, -105.0776977), DENVER_PHONE, 4, false),
        Station(41, "Castle Rock", "541 Topeka Way", "Castle Rock", "80104", GeoPoint(39.3764147, -104.8697492), DENVER_PHONE, 2, false),
        Station(45, "County Line", "8494 S. Colorado Blvd.", "Littleton", "80126", GeoPoint(39.5622974, -104.9411212), DENVER_PHONE, 5, false),
        Station(54, "Dacono", "5222 Silver Peak Ave.", "Dacono", "80514", GeoPoint(40.0764052, -104.9769456), DENVER_PHONE, 2, false),
        Station(48, "Denver (Lipan)", "2300 S. Lipan St.", "Denver", "80223", GeoPoint(39.6744566, -105.0014247), DENVER_PHONE, 7, false),
        Station(47, "Denver SE (Warren)", "10190 E. Warren Ave.", "Denver", "80231", GeoPoint(39.6763152, -104.8692687), DENVER_PHONE, 7, false),
        Station(51, "Fort Collins", "835 S.E. Frontage Rd.", "Fort Collins", "80524", GeoPoint(40.5791272, -104.9995034), NORTH_PHONE, 6, true),
        Station(40, "Golden", "15335 W. 44th Ave.", "Golden", "80401", GeoPoint(39.7765908, -105.1729234), DENVER_PHONE, 4, false),
        Station(52, "Greeley", "2844 W. 30th St.", "Greeley", "80631", GeoPoint(40.3873791, -104.7274433), NORTH_PHONE, 5, false),
        Station(44, "Ken Caryl", "10727 Centennial Rd.", "Littleton", "80127", GeoPoint(39.567583, -105.1192925), DENVER_PHONE, 5, true),
        Station(43, "Longmont", "4040 Rogers Rd.", "Longmont", "80503", GeoPoint(40.1583663, -105.1551809), DENVER_PHONE, 3, false),
        Station(53, "Loveland", "7001 N. Franklin Ave.", "Loveland", "80538", GeoPoint(40.4638362, -105.0859745), NORTH_PHONE, 4, false),
        Station(37, "Northglenn", "1950 E. 112th Ave.", "Northglenn", "80233", GeoPoint(39.8991113, -104.9640068), DENVER_PHONE, 7, false),
        Station(50, "Parker", "10199 Park Glenn Way", "Parker", "80134", GeoPoint(39.5326337, -104.7659041), DENVER_PHONE, 2, false),
        Station(39, "Sheridan", "2802 W. Mansfield Ave.", "Sheridan", "80110", GeoPoint(39.645118, -105.0238446), DENVER_PHONE, 7, true),
    )

    private val byId = all.associateBy { it.id }

    operator fun get(id: Int): Station? = byId[id]
}
