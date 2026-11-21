package io.cosgriff.waitlight.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * What AirCare's van page says a unit is doing right now, from its status image's `alt` text. Only today's tab
 * has live statuses; the rest say [NOT_ON_SITE_UNSCHEDULED].
 */
enum class VanLiveStatus {
    /** "On-site: Testing": reading cars. */
    TESTING,

    /** "On-site: Not Testing": set up, not reading. */
    NOT_TESTING,

    /** "On-site: Status Unknown". */
    UNKNOWN,

    /** "Not On-site: Currently Scheduled": due today, not there yet. */
    NOT_ON_SITE_SCHEDULED,

    /** "Not On-site: Not Currently Scheduled". */
    NOT_ON_SITE_UNSCHEDULED,
    ;

    companion object {
        private val space = Regex("""\s+""")

        /** The status in AirCare's words, or null for wording the app doesn't know. */
        fun parse(alt: String): VanLiveStatus? = when (alt.replace(space, " ").trim().lowercase()) {
            "on-site: testing" -> TESTING
            "on-site: not testing" -> NOT_TESTING
            "on-site: status unknown" -> UNKNOWN
            "not on-site: currently scheduled" -> NOT_ON_SITE_SCHEDULED
            "not on-site: not currently scheduled" -> NOT_ON_SITE_UNSCHEDULED
            else -> null
        }

        /** The map pin's icon for a status: icon9 testing, icon10 not testing, icon11 unknown, icon7 not on site. */
        fun fromIcon(icon: String): VanLiveStatus? = when (icon) {
            "icon9" -> TESTING
            "icon10" -> NOT_TESTING
            "icon11" -> UNKNOWN
            "icon7" -> NOT_ON_SITE_UNSCHEDULED
            else -> null
        }

        /** Whether the pin's [icon] says the same as the row's status: "not on site" has one icon for both kinds. */
        fun agrees(status: VanLiveStatus, icon: String): Boolean {
            val pin = fromIcon(icon) ?: return false
            val onSite = status == NOT_ON_SITE_SCHEDULED || status == NOT_ON_SITE_UNSCHEDULED
            return if (onSite) pin == NOT_ON_SITE_UNSCHEDULED else pin == status
        }
    }
}

/** A site's text split: "ON-RAMP TO EB I-70 FROM HARLAN ST." on Mon–Thu, 7:00 AM to 5:00 PM. */
data class VanSiteText(val description: String, val days: Set<DayOfWeek>, val opens: LocalTime, val closes: LocalTime) {
    companion object {
        private val schedule = Regex(
            """^(.*?)\s+((?:(?:Mon|Tues?|Wed|Thu(?:rs?)?|Fri)\s+)+)(\d{1,2}:\d{2}\s*[AP]M)\s*-\s*(\d{1,2}:\d{2}\s*[AP]M)\s*$""",
            RegexOption.IGNORE_CASE,
        )
        private val space = Regex("""\s+""")
        private val clock = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

        /**
         * The trailing `(<Mon|Tues|Wed|Thur|Fri> )+ h:mm AM - h:mm PM` and the description before it, or null when
         * the text doesn't end that way.
         */
        fun parse(text: String): VanSiteText? {
            val match = schedule.find(text.replace(' ', ' ').trim()) ?: return null
            val (description, dayText, opensText, closesText) = match.destructured
            val days = dayText.trim().split(space).map { day(it) ?: return null }.toSet()
            val opens = time(opensText) ?: return null
            val closes = time(closesText) ?: return null
            if (description.isBlank() || !opens.isBefore(closes)) return null
            return VanSiteText(description.trim(), days, opens, closes)
        }

        private fun day(text: String): DayOfWeek? = when (text.lowercase().take(3)) {
            "mon" -> DayOfWeek.MONDAY
            "tue" -> DayOfWeek.TUESDAY
            "wed" -> DayOfWeek.WEDNESDAY
            "thu" -> DayOfWeek.THURSDAY
            "fri" -> DayOfWeek.FRIDAY
            else -> null
        }

        private fun time(text: String): LocalTime? = try {
            LocalTime.parse(text.replace(space, " ").uppercase().replace(Regex("""(\d)([AP]M)"""), "$1 $2"), clock)
        } catch (_: DateTimeParseException) {
            null
        }
    }
}

/**
 * A van site's name for the list, rewritten from AirCare's text, which is all capitals and words ramps the long
 * way round. Typed, so `:app` words it ("Harlan St → EB I-70", "NB Taft Hill Rd past Harmony Rd"); the road names
 * inside are AirCare's, tidied.
 */
sealed interface VanName {
    /** An on-ramp: from [from] onto [onto] ("Harlan St" onto "EB I-70"). */
    data class Ramp(val from: String, val onto: String) : VanName

    /** A unit on a road, past a cross street ("NB Taft Hill Rd" past "Harmony Rd"). */
    data class Road(val road: String, val past: String) : VanName

    /** Text that fits neither, tidied. */
    data class Plain(val text: String) : VanName

    companion object {
        private val twoUnits = Regex("""\(\s*two\s+units\s+(?:at|on)\s+site\s*\)""", RegexOption.IGNORE_CASE)
        private val exit = Regex("""\s*(\(\s*exit\s+\d+\s*\))\s*$""", RegexOption.IGNORE_CASE)
        private val onRamp = Regex("""^on-ramp\s+to\s+(.+?)\s+from\s+(.+)$""", RegexOption.IGNORE_CASE)
        private val from = Regex("""^(.+?)\s+from\s+(.+)$""", RegexOption.IGNORE_CASE)
        private val after = Regex("""^(.+?)\s+(?:just\s+)?after\s+(?:intersection\s+with\s+)?(.+)$""", RegexOption.IGNORE_CASE)
        private val space = Regex("""\s+""")

        /** Whether the site has two units ("(two units at site)" in its text). */
        fun hasTwoUnits(description: String): Boolean = twoUnits.containsMatchIn(description)

        /** The name for [description] (a site's text without its days and hours). */
        fun of(description: String): VanName {
            var text = description.replace(twoUnits, " ").replace(space, " ").trim().removeSuffix(".").trim()
            // "(Exit 184)" belongs with the highway: "Founders Pkwy → NB I-25 (Exit 184)".
            val exitNote = exit.find(text)?.groupValues?.get(1)
            if (exitNote != null) text = text.replace(exit, "")
            fun withExit(onto: String) = if (exitNote == null) onto else "$onto ${tidy(exitNote)}"
            onRamp.find(text)?.let { return Ramp(tidy(it.groupValues[2]), withExit(tidy(it.groupValues[1]))) }
            from.find(text)?.let { return Ramp(tidy(it.groupValues[2]), withExit(tidy(it.groupValues[1]))) }
            after.find(text)?.let { return Road(tidy(it.groupValues[1]), withExit(tidy(it.groupValues[2]))) }
            return Plain(withExit(tidy(text)))
        }

        private val roadCode = Regex("""^(?:I|US|SH|SR|CO|CR|LCR|C|E)-\d+[A-Z]?$""", RegexOption.IGNORE_CASE)
        private val upper = setOf("NB", "SB", "EB", "WB", "CR", "US", "SH", "SR", "CO", "LCR", "N", "S", "E", "W")
        private val fixes = mapOf("PENA" to "Peña")

        /** AirCare's capitals as a name is written: road codes and directions kept, "ST." to "St", "27TH" to "27th". */
        internal fun tidy(text: String): String = text.replace(space, " ").trim().split(' ').joinToString(" ") { word ->
            val (open, core, trailing) = wordParts.matchEntire(word)!!.destructured
            val tidied = when {
                core.isEmpty() -> core
                roadCode.matches(core) -> core.uppercase()
                core.uppercase() in upper -> core.uppercase()
                core.uppercase() in fixes -> fixes.getValue(core.uppercase())
                core.first().isDigit() -> core.lowercase()
                else -> core.lowercase().replaceFirstChar { it.titlecase(Locale.ROOT) }
            }
            // "ST." is "St"; a closing bracket stays.
            open + tidied + trailing.filter { it == ')' }
        }

        private val wordParts = Regex("""^(\(*)(.*?)([).,]*)$""")
    }
}

/**
 * One site on one of the van page's tabs: its description (the text without days and hours), where AirCare pins
 * it, the weekdays and hours it's posted for, AirCare's city for it (often wrong, so only a fallback), and the
 * live status the page gave it. Two tabs listing the same site give two of these with the same [key].
 */
data class VanSite(
    val description: String,
    val point: GeoPoint,
    val days: Set<DayOfWeek>,
    val opens: LocalTime,
    val closes: LocalTime,
    val city: String,
    val status: VanLiveStatus?,
) {
    /** The same site from week to week ([siteKey]); never AirCare's `locationID`, which changes between fetches. */
    val key: String get() = siteKey(point, description)

    val name: VanName get() = VanName.of(description)

    val twoUnits: Boolean get() = VanName.hasTwoUnits(description)

    /** A `geo:` link to the site, as for a station, for the map app the user picks. */
    fun mapUri(label: String): String {
        val at = "${point.latitude},${point.longitude}"
        return "geo:$at?q=$at(" + java.net.URLEncoder.encode(label, Charsets.UTF_8).replace("+", "%20") + ")"
    }

    companion object {
        private val space = Regex("""\s+""")

        /** Whitespace collapsed, trimmed, a trailing dot dropped, lower case: how two tabs' texts for a site match. */
        fun normalise(text: String): String = text.replace('\u00A0', ' ').replace(space, " ").trim().removeSuffix(".").trimEnd().lowercase()

        /**
         * A site's key: its coordinates to 5 decimals (half up, from their shortest decimal text) and its normalised
         * description, "39.78313,-105.05980|on-ramp to eb i-70 from harlan st". `scripts/van_drive_times.py` makes
         * the same keys for the drive-time table.
         */
        fun siteKey(point: GeoPoint, description: String): String =
            "${coordinate(point.latitude)},${coordinate(point.longitude)}|${normalise(description)}"

        private fun coordinate(value: Double): String =
            BigDecimal(value.toString()).setScale(5, RoundingMode.HALF_UP).toPlainString()
    }
}
