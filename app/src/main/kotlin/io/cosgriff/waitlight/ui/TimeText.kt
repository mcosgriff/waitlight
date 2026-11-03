package io.cosgriff.waitlight.ui

import android.text.format.DateFormat
import io.cosgriff.waitlight.domain.AirCareHours
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Times as the stations keep them, in Denver's time, in the phone's own 12- or 24-hour style: "8 AM" on the hour,
 * "2:14 PM" otherwise.
 */
object TimeText {
    fun time(at: Instant, locale: Locale = Locale.getDefault()): String = time(at.atZone(AirCareHours.zone), locale)

    fun time(at: ZonedDateTime, locale: Locale = Locale.getDefault()): String =
        format(at, if (at.minute == 0) "j" else "jmm", locale)

    /** The time, with the weekday in front when it isn't [now]'s day: "Mon 2:14 PM". */
    fun timeSince(at: Instant, now: Instant, locale: Locale = Locale.getDefault()): String {
        val local = at.atZone(AirCareHours.zone)
        val sameDay = local.toLocalDate() == now.atZone(AirCareHours.zone).toLocalDate()
        return if (sameDay) time(local, locale) else format(local, if (local.minute == 0) "EEEj" else "EEEjmm", locale)
    }

    /** "Monday". */
    fun weekday(at: ZonedDateTime, locale: Locale = Locale.getDefault()): String = format(at, "EEEE", locale)

    /** "Mon". */
    fun shortWeekday(at: ZonedDateTime, locale: Locale = Locale.getDefault()): String = format(at, "EEE", locale)

    private fun format(at: ZonedDateTime, skeleton: String, locale: Locale): String =
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale).format(at)
}
