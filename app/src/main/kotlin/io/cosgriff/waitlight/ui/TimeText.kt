package io.cosgriff.waitlight.ui

import android.text.format.DateFormat
import io.cosgriff.waitlight.domain.AirCareHours
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
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

    /** A time of day as the stations keep them: "7 AM", "6:35 AM". */
    fun time(at: LocalTime, locale: Locale = Locale.getDefault()): String =
        time(LocalDate.of(2026, 1, 1).atTime(at).atZone(AirCareHours.zone), locale)

    /** The date and time, for a van fetch: "Nov 20, 5:57 PM". */
    fun dateTime(at: Instant, locale: Locale = Locale.getDefault()): String {
        val local = at.atZone(AirCareHours.zone)
        return format(local, if (local.minute == 0) "MMMdj" else "MMMdjmm", locale)
    }

    /** "Monday". */
    fun weekday(date: LocalDate, locale: Locale = Locale.getDefault()): String = weekday(date.atStartOfDay(AirCareHours.zone), locale)

    /** "Mon". */
    fun shortWeekday(date: LocalDate, locale: Locale = Locale.getDefault()): String = shortWeekday(date.atStartOfDay(AirCareHours.zone), locale)

    /** "Thursday, Nov 26". */
    fun longDate(date: LocalDate, locale: Locale = Locale.getDefault()): String = format(date.atStartOfDay(AirCareHours.zone), "EEEEMMMd", locale)

    /** "Thu, Nov 26". */
    fun shortDate(date: LocalDate, locale: Locale = Locale.getDefault()): String = format(date.atStartOfDay(AirCareHours.zone), "EEEMMMd", locale)

    /** "Mon, Aug 11, 2025". */
    fun shortDateWithYear(date: LocalDate, locale: Locale = Locale.getDefault()): String = format(date.atStartOfDay(AirCareHours.zone), "yEEEMMMd", locale)

    /** "Fri 20". */
    fun weekdayAndDay(date: LocalDate, locale: Locale = Locale.getDefault()): String = format(date.atStartOfDay(AirCareHours.zone), "EEEd", locale)

    /** "23". */
    fun dayOfMonth(date: LocalDate, locale: Locale = Locale.getDefault()): String = format(date.atStartOfDay(AirCareHours.zone), "d", locale)

    private fun format(at: ZonedDateTime, skeleton: String, locale: Locale): String =
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale).format(at)
}
