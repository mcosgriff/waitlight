package io.cosgriff.waitlight.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/** The Colorado state holidays AirCare closes for (C.R.S. 24-11-101; the state's 2026 holiday schedule). */
enum class Holiday {
    NEW_YEARS_DAY,
    MARTIN_LUTHER_KING_JR_DAY,
    PRESIDENTS_DAY,
    MEMORIAL_DAY,
    JUNETEENTH,
    INDEPENDENCE_DAY,
    LABOR_DAY,
    CABRINI_DAY,
    VETERANS_DAY,
    THANKSGIVING,
    CHRISTMAS,
    ;

    /** The holiday's own date in [year]. */
    fun dateIn(year: Int): LocalDate = when (this) {
        NEW_YEARS_DAY -> LocalDate.of(year, 1, 1)
        MARTIN_LUTHER_KING_JR_DAY -> nth(year, 1, 3, DayOfWeek.MONDAY)
        PRESIDENTS_DAY -> nth(year, 2, 3, DayOfWeek.MONDAY)
        MEMORIAL_DAY -> LocalDate.of(year, 5, 1).with(TemporalAdjusters.lastInMonth(DayOfWeek.MONDAY))
        JUNETEENTH -> LocalDate.of(year, 6, 19)
        INDEPENDENCE_DAY -> LocalDate.of(year, 7, 4)
        LABOR_DAY -> nth(year, 9, 1, DayOfWeek.MONDAY)
        CABRINI_DAY -> nth(year, 10, 1, DayOfWeek.MONDAY)
        VETERANS_DAY -> LocalDate.of(year, 11, 11)
        THANKSGIVING -> nth(year, 11, 4, DayOfWeek.THURSDAY)
        CHRISTMAS -> LocalDate.of(year, 12, 25)
    }

    /** The weekday the state observes it on: a Saturday holiday moves to Friday, a Sunday one to Monday. */
    fun observedIn(year: Int): LocalDate {
        val date = dateIn(year)
        return when (date.dayOfWeek) {
            DayOfWeek.SATURDAY -> date.minusDays(1)
            DayOfWeek.SUNDAY -> date.plusDays(1)
            else -> date
        }
    }

    private companion object {
        fun nth(year: Int, month: Int, n: Int, day: DayOfWeek): LocalDate =
            LocalDate.of(year, month, 1).with(TemporalAdjusters.dayOfWeekInMonth(n, day))
    }
}

/**
 * When the stations are open. The same for all of them: Mon–Fri 8:00–17:30, Sat 8:00–13:00, closed Sundays and
 * state holidays, in Denver's time. Hard-coded (ADR-0003); the fetch compares it with the hours the page states.
 */
object AirCareHours {
    val zone: ZoneId = ZoneId.of("America/Denver")

    private val opens: LocalTime = LocalTime.of(8, 0)
    private val weekdayCloses: LocalTime = LocalTime.of(17, 30)
    private val saturdayCloses: LocalTime = LocalTime.of(13, 0)

    /** AirCare's page states the hours in these words; the fetch logs when they change. */
    const val STATED_HOURS = "8am to 5:30pm (M-F),8am to 1pm (Sat), closed Sunday and state holidays"

    /** The holiday [date] closes for, if any. */
    fun holidayOn(date: LocalDate): Holiday? = Holiday.entries.firstOrNull { holiday ->
        // A New Year's Day on a Saturday is observed on the last day of the year before.
        listOf(date.year, date.year + 1).any { year -> closesFor(holiday, year, date) }
    }

    /**
     * A holiday closes the stations on the day the state observes it and, when it falls on a Saturday, on the day
     * itself too: the stations are otherwise open Saturdays, and AirCare says only "closed state holidays".
     */
    private fun closesFor(holiday: Holiday, year: Int, date: LocalDate): Boolean {
        val own = holiday.dateIn(year)
        return date == holiday.observedIn(year) || (date == own && own.dayOfWeek == DayOfWeek.SATURDAY)
    }

    /** Opening and closing time on [date], or null when closed all day. */
    fun hoursOn(date: LocalDate): ClosedRange<LocalTime>? = when {
        holidayOn(date) != null -> null
        date.dayOfWeek == DayOfWeek.SUNDAY -> null
        date.dayOfWeek == DayOfWeek.SATURDAY -> opens..saturdayCloses
        else -> opens..weekdayCloses
    }

    /** Open at [at]: from opening time up to, not including, closing time. */
    fun isOpen(at: Instant): Boolean {
        val local = at.atZone(zone)
        val hours = hoursOn(local.toLocalDate()) ?: return false
        val time = local.toLocalTime()
        return time >= hours.start && time < hours.endInclusive
    }

    /** When the stations next open after [after]: later today, tomorrow, or after a weekend or holiday. */
    fun nextOpening(after: Instant): ZonedDateTime {
        val local = after.atZone(zone)
        var date = local.toLocalDate()
        repeat(MAX_DAYS_CLOSED) {
            val hours = hoursOn(date)
            if (hours != null) {
                val opening = date.atTime(hours.start).atZone(zone)
                if (opening.toInstant() > after) return opening
            }
            date = date.plusDays(1)
        }
        error("No opening within $MAX_DAYS_CLOSED days of $local")
    }

    /** When the stations last closed before [before], for "Closed since 5:30 PM". */
    fun lastClosing(before: Instant): ZonedDateTime {
        var date = before.atZone(zone).toLocalDate()
        repeat(MAX_DAYS_CLOSED) {
            val hours = hoursOn(date)
            if (hours != null) {
                val closing = date.atTime(hours.endInclusive).atZone(zone)
                if (closing.toInstant() <= before) return closing
            }
            date = date.minusDays(1)
        }
        error("No closing within $MAX_DAYS_CLOSED days before ${before.atZone(zone)}")
    }

    /** When the stations close today, if they are open at [at]. */
    fun closingTime(at: Instant): ZonedDateTime? {
        if (!isOpen(at)) return null
        val local = at.atZone(zone)
        return local.toLocalDate().atTime(hoursOn(local.toLocalDate())!!.endInclusive).atZone(zone)
    }

    /** No run of closed days is this long (the longest is a Sunday next to a holiday or two). */
    private const val MAX_DAYS_CLOSED = 14
}

/** Which day the next opening falls on, relative to now, for wording ("at 8 AM", "tomorrow", "on Monday"). */
enum class OpeningDay { TODAY, TOMORROW, LATER }

fun openingDay(now: Instant, opening: ZonedDateTime): OpeningDay {
    val today = now.atZone(opening.zone).toLocalDate()
    return when (opening.toLocalDate()) {
        today -> OpeningDay.TODAY
        today.plusDays(1) -> OpeningDay.TOMORROW
        else -> OpeningDay.LATER
    }
}
