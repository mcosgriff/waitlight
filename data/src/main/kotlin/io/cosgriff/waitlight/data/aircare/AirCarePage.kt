package io.cosgriff.waitlight.data.aircare

import io.cosgriff.waitlight.domain.AirCareHours
import io.cosgriff.waitlight.domain.AirCareTimestamp
import io.cosgriff.waitlight.domain.Stations
import io.cosgriff.waitlight.domain.WaitReading
import io.cosgriff.waitlight.domain.WaitText
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.time.Instant

/** What reading AirCare's page gave. */
sealed interface PageRead {
    /**
     * Every station on the page read. [waits] is keyed by AirCare's `locationID` ([Stations]); a station AirCare
     * added since the app was built is left out. [hoursAsStated] is false when any station's stated hours differ
     * from [AirCareHours.STATED_HOURS], which the hard-coded hours should then be checked against.
     */
    data class Read(val waits: Map<Int, Pair<WaitReading, Instant>>, val hoursAsStated: Boolean) : PageRead

    /** The page isn't the one the app knows: AirCare changed its site. [why] is for the log. */
    data class Unreadable(val why: String) : PageRead
}

/**
 * Reads AirCare's wait-times page. The page draws its map from a `var latlngs = [...]` script array, one object per
 * station: `locationID`, `name` (the station's name and its wait wording) and `infoWindow` (HTML with the hours
 * and the "Last updated" time). Anything not as expected makes the whole page [PageRead.Unreadable], so the app
 * never shows a wait it half-read.
 */
object AirCarePage {
    private val start = Regex("""\bvar\s+latlngs\s*=\s*\[""")
    private val tag = Regex("<[^>]*>")
    private val hours = Regex("""Hours of Operation\s*:\s*(.*?)\s*Last updated""", RegexOption.IGNORE_CASE)
    private val updated = Regex(
        """Last updated\s*:\s*(\d{1,2}/\d{1,2}/\d{4}\s+\d{1,2}:\d{2}(?::\d{2})?\s*[AP]M)""",
        RegexOption.IGNORE_CASE,
    )
    private val space = Regex("""\s+""")

    fun read(html: String): PageRead {
        val array = stationArray(html) ?: return PageRead.Unreadable("no latlngs array")
        val entries = try {
            Json.parseToJsonElement(array) as? JsonArray ?: return PageRead.Unreadable("latlngs isn't an array")
        } catch (e: SerializationException) {
            return PageRead.Unreadable("latlngs isn't JSON: ${e.message}")
        }
        if (entries.isEmpty()) return PageRead.Unreadable("no stations")

        val waits = mutableMapOf<Int, Pair<WaitReading, Instant>>()
        var hoursAsStated = true
        for (entry in entries) {
            val station = entry as? JsonObject ?: return PageRead.Unreadable("a station isn't an object")
            val id = station.int("locationID") ?: return PageRead.Unreadable("a station has no locationID")
            val name = station.text("name") ?: return PageRead.Unreadable("station $id has no name")
            val info = station.text("infoWindow")?.let(::plainText)
                ?: return PageRead.Unreadable("station $id has no infoWindow")

            val reading = WaitText.parse(name) ?: return PageRead.Unreadable("station $id: new wording \"$name\"")
            val at = updated.find(info)?.groupValues?.get(1)?.let(AirCareTimestamp::parse)
                ?: return PageRead.Unreadable("station $id: no readable \"Last updated\"")
            val stated = hours.find(info)?.groupValues?.get(1)?.let(::normalize)
            if (stated != normalize(AirCareHours.STATED_HOURS)) hoursAsStated = false

            if (Stations[id] != null) waits[id] = reading to at
        }
        if (waits.isEmpty()) return PageRead.Unreadable("none of the stations is one the app knows")
        return PageRead.Read(waits, hoursAsStated)
    }

    /** The JSON text of the `latlngs` array: from its `[` to the matching `]`, skipping brackets inside strings. */
    private fun stationArray(html: String): String? {
        val open = start.find(html)?.range?.last ?: return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in open until html.length) {
            val c = html[i]
            when {
                escaped -> escaped = false
                inString && c == '\\' -> escaped = true
                c == '"' -> inString = !inString
                inString -> Unit
                c == '[' -> depth++
                c == ']' -> if (--depth == 0) return html.substring(open, i + 1)
            }
        }
        return null
    }

    /** The infoWindow's text: tags dropped (the hours run across a `<br>`), the few entities AirCare uses decoded. */
    private fun plainText(html: String): String =
        html.replace(tag, "").replace("&nbsp;", " ").replace("&amp;", "&")

    private fun normalize(text: String) = text.replace(space, " ").trim().lowercase()

    private fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.int(key: String) = (get(key) as? JsonPrimitive)?.intOrNull
}
