package io.cosgriff.waitlight.data.vans

import io.cosgriff.waitlight.domain.GeoPoint
import io.cosgriff.waitlight.domain.VanLiveStatus
import io.cosgriff.waitlight.domain.VanSite
import io.cosgriff.waitlight.domain.VanSiteText
import io.cosgriff.waitlight.domain.VanTab
import io.cosgriff.waitlight.domain.VanTabKind
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** One tab of the van page before its table is fetched. */
data class VanTabSource(
    val title: String,
    val kind: VanTabKind,
    /** The map's pins, or null when the tab has none to read. */
    val pins: List<VanPin>?,
    /** The `day` id of the tab's table, from its iframe; opaque, so read from the page, never guessed. */
    val day: Int?,
    /** Why the tab can't be read: AirCare's own words when it shows an error, for the log. */
    val problem: String?,
)

/** A map pin: the site's full text (description, days and hours), where it is, and its status icon. */
data class VanPin(val text: String, val point: GeoPoint, val icon: String)

/** A row of a tab's table: AirCare's city, the status image's `alt`, and the site's full text. */
data class VanTableRow(val city: String, val status: String, val text: String)

/** What reading the van page gave. */
sealed interface VanPageRead {
    data class Read(val tabs: List<VanTabSource>) : VanPageRead

    /** Not the page the app knows: AirCare changed it. [why] is for the log. */
    data class Unreadable(val why: String) : VanPageRead
}

/**
 * Reads AirCare's van page (`/locations/van-locations`). It has four tabs, each an `<h3 class="jl_magic_tabs …">`
 * with its title, followed by the tab's content up to the next one: an inline `var latlngs = [...]` script (one
 * pin per site: `name`, `lat`, `lng`, `icon`) and an iframe whose `src` names the table of the same sites with
 * their city and live status. A tab with neither is unavailable (AirCare's error page lands inside it), not a
 * reason to drop the others; a page with no tab the app can read is [VanPageRead.Unreadable].
 */
object VanPage {
    private val tabHeading = Regex("""<h3\b[^>]*class="[^"]*\bjl_magic_tabs\b[^"]*"[^>]*>(.*?)</h3>""", RegexOption.DOT_MATCHES_ALL)
    private val latlngs = Regex("""\bvar\s+latlngs\s*=\s*\[""")
    private val iframeDay = Regex("""<iframe\b[^>]*\bsrc="[^"]*vanlocationstabledisplay[^"]*[?&]day=(\d+)""", RegexOption.IGNORE_CASE)
    private val errorHeading = Regex("""<h3>\s*([^<]*error[^<]*)</h3>""", RegexOption.IGNORE_CASE)
    private val row = Regex("""<tr\b[^>]*>(.*?)</tr>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val cell = Regex("""<td\b[^>]*>(.*?)</td>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val alt = Regex("""\balt="([^"]*)"""", RegexOption.IGNORE_CASE)
    private val tag = Regex("<[^>]*>")
    private val space = Regex("""\s+""")

    fun read(html: String): VanPageRead {
        val headings = tabHeading.findAll(html).toList()
        if (headings.isEmpty()) return VanPageRead.Unreadable("no tabs")
        val tabs = headings.mapIndexedNotNull { i, heading ->
            val title = text(heading.groupValues[1])
            val kind = VanTabKind.parse(title) ?: return@mapIndexedNotNull null
            val end = headings.getOrNull(i + 1)?.range?.first ?: html.length
            tab(title, kind, html.substring(heading.range.last + 1, end))
        }
        if (tabs.none { it.pins != null }) return VanPageRead.Unreadable("no tab has a map: ${tabs.mapNotNull { it.problem }}")
        return VanPageRead.Read(tabs)
    }

    private fun tab(title: String, kind: VanTabKind, html: String): VanTabSource {
        val day = iframeDay.find(html)?.groupValues?.get(1)?.toIntOrNull()
        val array = bracketed(html)
        val problem = errorHeading.find(html)?.groupValues?.get(1)?.trim()
        if (array == null) return VanTabSource(title, kind, null, day, problem ?: "no latlngs")
        val pins = try {
            (Json.parseToJsonElement(array) as? JsonArray)?.map { entry ->
                val pin = entry as? JsonObject ?: return VanTabSource(title, kind, null, day, "a pin isn't an object")
                val text = pin.text("name") ?: return VanTabSource(title, kind, null, day, "a pin has no name")
                val lat = pin.text("lat")?.toDoubleOrNull()
                val lng = pin.text("lng")?.toDoubleOrNull()
                if (lat == null || lng == null) return VanTabSource(title, kind, null, day, "$text has no coordinates")
                VanPin(text, GeoPoint(lat, lng), pin.text("icon").orEmpty())
            }
        } catch (e: SerializationException) {
            return VanTabSource(title, kind, null, day, "latlngs isn't JSON: ${e.message}")
        } ?: return VanTabSource(title, kind, null, day, "latlngs isn't an array")
        return VanTabSource(title, kind, pins, day, null)
    }

    /** The rows of a tab's table (`vanlocationstabledisplay_new.php?day=N`). Its markup is sloppy; rows are read by their cells. */
    fun rows(html: String): List<VanTableRow> = row.findAll(html).mapNotNull { tr ->
        val cells = cell.findAll(tr.groupValues[1]).map { it.groupValues[1] }.toList()
        if (cells.size < 3) return@mapNotNull null
        val status = alt.find(cells[1])?.groupValues?.get(1) ?: text(cells[1])
        VanTableRow(text(cells[0]), text(status), text(cells[2]))
    }.toList()

    /**
     * The tab's sites: each pin joined to its table row on normalised text, the pin giving where it is, the row its
     * city and live status. [rows] is null when the table couldn't be fetched: the sites then have no status. A pin
     * whose text has no days and hours is left out, and so is a pin with no row when there is a table.
     */
    fun join(source: VanTabSource, rows: List<VanTableRow>?, log: (String) -> Unit = {}): VanTab {
        val pins = source.pins ?: return VanTab(source.kind, null, source.problem)
        val byText = rows?.associateBy { VanSite.normalise(it.text) }
        val sites = pins.mapNotNull { pin ->
            val parsed = VanSiteText.parse(pin.text) ?: run {
                log("${source.title}: no days and hours in \"${pin.text}\"")
                return@mapNotNull null
            }
            val row = byText?.get(VanSite.normalise(pin.text))
            if (byText != null && row == null) {
                log("${source.title}: no table row for \"${pin.text}\"")
                return@mapNotNull null
            }
            val status = row?.let { VanLiveStatus.parse(it.status) }
            if (row != null && status == null) log("${source.title}: new status wording \"${row.status}\"")
            if (status != null && !VanLiveStatus.agrees(status, pin.icon)) {
                log("${source.title}: \"${pin.text}\" is ${row.status} but its pin is ${pin.icon}")
            }
            VanSite(parsed.description, pin.point, parsed.days, parsed.opens, parsed.closes, row?.city.orEmpty(), status)
        }
        return VanTab(source.kind, sites)
    }

    private fun text(html: String) = html.replace(tag, " ").replace("&nbsp;", " ").replace("&amp;", "&")
        .replace(space, " ").trim()

    /** The JSON text of the `latlngs` array: from its `[` to the matching `]`, skipping brackets inside strings. */
    private fun bracketed(html: String): String? {
        val open = latlngs.find(html)?.range?.last ?: return null
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

    private fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull
}
