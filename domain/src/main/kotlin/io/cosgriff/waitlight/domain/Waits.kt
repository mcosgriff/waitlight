package io.cosgriff.waitlight.domain

import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** One station's wait, as AirCare words it. */
sealed interface WaitReading {
    /** "Wait is 13 mins". */
    data class Minutes(val minutes: Int) : WaitReading {
        init {
            require(minutes >= 0) { "a wait can't be negative: $minutes" }
        }
    }

    /** "The wait is more than 60 minutes." No upper bound is given. */
    data class MoreThan(val minutes: Int) : WaitReading

    /** "Station Open": open, but no estimate. */
    data object NoEstimate : WaitReading
}

/** Reads AirCare's wait wording. */
object WaitText {
    private val minutes = Regex("""\bwait is (\d+) min""", RegexOption.IGNORE_CASE)
    private val moreThan = Regex("""\bmore than (\d+) min""", RegexOption.IGNORE_CASE)
    private val open = Regex("""\bstation open\b""", RegexOption.IGNORE_CASE)

    /** The reading in [text] (with or without the station's name in front), or null when the wording is new. */
    fun parse(text: String): WaitReading? {
        moreThan.find(text)?.let { return WaitReading.MoreThan(it.groupValues[1].toInt()) }
        minutes.find(text)?.let { return WaitReading.Minutes(it.groupValues[1].toInt()) }
        if (open.containsMatchIn(text)) return WaitReading.NoEstimate
        return null
    }
}

/** Reads the "Last updated" time on each station, which is Denver's local time. */
object AirCareTimestamp {
    // Seen: "11/2/2026 4:14:15 PM" and, on one station, "11/02/2026 04:16 PM".
    private val format = DateTimeFormatter.ofPattern("M/d/uuuu h:mm[:ss] a", Locale.US)

    fun parse(text: String): Instant? = try {
        LocalDateTime.parse(text.trim().uppercase(Locale.US), format).atZone(AirCareHours.zone).toInstant()
    } catch (_: DateTimeParseException) {
        null
    }
}

/** Where the badge colours change. Inclusive at the low end: 30 is moderate, 60 is moderate, 61 is long. */
data class WaitThresholds(val greenMax: Int = 30, val yellowMax: Int = 60) {
    init {
        require(greenMax in 1..<yellowMax) { "need 0 < greenMax < yellowMax, got $greenMax and $yellowMax" }
    }
}

enum class WaitBand { SHORT, MODERATE, LONG }

/** The band for a reading, or null when there is no estimate. */
fun WaitReading.band(thresholds: WaitThresholds): WaitBand? {
    val minutes = when (this) {
        is WaitReading.Minutes -> minutes
        // "More than N" is at least N + 1.
        is WaitReading.MoreThan -> minutes + 1
        WaitReading.NoEstimate -> return null
    }
    return when {
        minutes < thresholds.greenMax -> WaitBand.SHORT
        minutes <= thresholds.yellowMax -> WaitBand.MODERATE
        else -> WaitBand.LONG
    }
}

/** The figure a badge shows: the minutes, or "60+" for anything long. */
sealed interface ShownWait {
    data class Exact(val minutes: Int) : ShownWait
    data class Over(val minutes: Int) : ShownWait
}

fun WaitReading.shown(thresholds: WaitThresholds): ShownWait? = when {
    band(thresholds) == WaitBand.LONG -> ShownWait.Over(thresholds.yellowMax)
    this is WaitReading.Minutes -> ShownWait.Exact(minutes)
    this is WaitReading.MoreThan -> ShownWait.Over(minutes)
    else -> null
}

/** A station's badge. Colour is never the only signal: each kind has its own icon and words. */
sealed interface Badge {
    data class Live(val band: WaitBand, val wait: ShownWait) : Badge

    /** Open, no estimate. */
    data object Open : Badge

    /** Outside opening hours. */
    data object Closed : Badge

    /** The last reading we have, greyed: older than [Freshness.STALE_AFTER]. [band] and [wait] are null if that reading was "Station Open". */
    data class Stale(val band: WaitBand?, val wait: ShownWait?, val asOf: Instant) : Badge
}

/**
 * The badge for one station, from its last reading and AirCare's "Last updated" time for it. [dataUsable] is false
 * when the latest fetch couldn't be read (the page changed): the last good readings then all show as stale.
 */
fun badgeFor(
    reading: WaitReading,
    updatedAt: Instant,
    now: Instant,
    thresholds: WaitThresholds,
    dataUsable: Boolean = true,
): Badge {
    if (!AirCareHours.isOpen(now)) return Badge.Closed
    if (!dataUsable || !Freshness.isFresh(updatedAt, now)) {
        return Badge.Stale(reading.band(thresholds), reading.shown(thresholds), updatedAt)
    }
    return when (val band = reading.band(thresholds)) {
        null -> Badge.Open
        else -> Badge.Live(band, reading.shown(thresholds)!!)
    }
}
