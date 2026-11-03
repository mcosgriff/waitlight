package io.cosgriff.waitlight.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

class HoursTest {
    private fun denver(text: String): Instant = LocalDateTime.parse(text).atZone(AirCareHours.zone).toInstant()

    @Test
    fun `2026 matches the state's published holiday schedule`() {
        val observed = Holiday.entries.associateWith { it.observedIn(2026) }
        assertEquals(
            mapOf(
                Holiday.NEW_YEARS_DAY to LocalDate.of(2026, 1, 1),
                // The state's PDF prints "Jan. 16", but it is "3rd Monday in January", the 19th.
                Holiday.MARTIN_LUTHER_KING_JR_DAY to LocalDate.of(2026, 1, 19),
                Holiday.PRESIDENTS_DAY to LocalDate.of(2026, 2, 16),
                Holiday.MEMORIAL_DAY to LocalDate.of(2026, 5, 25),
                Holiday.JUNETEENTH to LocalDate.of(2026, 6, 19),
                Holiday.INDEPENDENCE_DAY to LocalDate.of(2026, 7, 3),
                Holiday.LABOR_DAY to LocalDate.of(2026, 9, 7),
                Holiday.CABRINI_DAY to LocalDate.of(2026, 10, 5),
                Holiday.VETERANS_DAY to LocalDate.of(2026, 11, 11),
                Holiday.THANKSGIVING to LocalDate.of(2026, 11, 26),
                Holiday.CHRISTMAS to LocalDate.of(2026, 12, 25),
            ),
            observed,
        )
    }

    @Test
    fun `a Saturday holiday closes both the observed Friday and the Saturday`() {
        assertEquals(Holiday.INDEPENDENCE_DAY, AirCareHours.holidayOn(LocalDate.of(2026, 7, 3)))
        assertEquals(Holiday.INDEPENDENCE_DAY, AirCareHours.holidayOn(LocalDate.of(2026, 7, 4)))
        assertNull(AirCareHours.hoursOn(LocalDate.of(2026, 7, 4)))
    }

    @Test
    fun `a Sunday holiday closes the Monday after`() {
        // July 4, 2027 is a Sunday.
        assertEquals(Holiday.INDEPENDENCE_DAY, AirCareHours.holidayOn(LocalDate.of(2027, 7, 5)))
    }

    @Test
    fun `New Year's Day on a Saturday is observed on December 31st`() {
        // January 1, 2028 is a Saturday.
        assertEquals(Holiday.NEW_YEARS_DAY, AirCareHours.holidayOn(LocalDate.of(2027, 12, 31)))
        assertEquals(Holiday.NEW_YEARS_DAY, AirCareHours.holidayOn(LocalDate.of(2028, 1, 1)))
    }

    @Test
    fun `an ordinary day is no holiday`() {
        assertNull(AirCareHours.holidayOn(LocalDate.of(2026, 11, 2)))
    }

    @Test
    fun `weekday hours run from 8 up to 5 30`() {
        assertFalse(AirCareHours.isOpen(denver("2026-11-02T07:59:59")))
        assertTrue(AirCareHours.isOpen(denver("2026-11-02T08:00")))
        assertTrue(AirCareHours.isOpen(denver("2026-11-02T17:29:59")))
        assertFalse(AirCareHours.isOpen(denver("2026-11-02T17:30")))
    }

    @Test
    fun `Saturday closes at 1 and Sunday is closed`() {
        assertTrue(AirCareHours.isOpen(denver("2026-11-07T12:59")))
        assertFalse(AirCareHours.isOpen(denver("2026-11-07T13:00")))
        assertFalse(AirCareHours.isOpen(denver("2026-11-08T10:00")))
    }

    @Test
    fun `closed on a holiday weekday`() {
        assertFalse(AirCareHours.isOpen(denver("2026-11-26T10:00")))
    }

    @Test
    fun `next opening after a weekday close is tomorrow at 8`() {
        val now = denver("2026-11-02T18:00")
        val next = AirCareHours.nextOpening(now)
        assertEquals(LocalDateTime.parse("2026-11-03T08:00"), next.toLocalDateTime())
        assertEquals(OpeningDay.TOMORROW, openingDay(now, next))
    }

    @Test
    fun `last closing is today's, yesterday's, or Saturday's over a weekend`() {
        assertEquals(LocalDateTime.parse("2026-11-02T17:30"), AirCareHours.lastClosing(denver("2026-11-02T18:00")).toLocalDateTime())
        assertEquals(LocalDateTime.parse("2026-11-02T17:30"), AirCareHours.lastClosing(denver("2026-11-03T06:30")).toLocalDateTime())
        // Sunday morning: Saturday closed at 1.
        assertEquals(LocalDateTime.parse("2026-11-07T13:00"), AirCareHours.lastClosing(denver("2026-11-08T09:00")).toLocalDateTime())
        // Veterans Day, Wednesday 2026-11-11: closed since the Tuesday.
        assertEquals(LocalDateTime.parse("2026-11-10T17:30"), AirCareHours.lastClosing(denver("2026-11-11T12:00")).toLocalDateTime())
    }

    @Test
    fun `next opening early in the morning is today`() {
        val now = denver("2026-11-03T06:30")
        val next = AirCareHours.nextOpening(now)
        assertEquals(LocalDateTime.parse("2026-11-03T08:00"), next.toLocalDateTime())
        assertEquals(OpeningDay.TODAY, openingDay(now, next))
    }

    @Test
    fun `next opening after Saturday at 1 is Monday`() {
        val now = denver("2026-11-07T13:30")
        val next = AirCareHours.nextOpening(now)
        assertEquals(LocalDateTime.parse("2026-11-09T08:00"), next.toLocalDateTime())
        assertEquals(OpeningDay.LATER, openingDay(now, next))
    }

    @Test
    fun `next opening skips a holiday`() {
        // Wednesday evening before Thanksgiving: Thursday is closed, Friday opens.
        val next = AirCareHours.nextOpening(denver("2026-11-25T18:00"))
        assertEquals(LocalDateTime.parse("2026-11-27T08:00"), next.toLocalDateTime())
    }

    @Test
    fun `next opening skips a Sunday followed by a holiday`() {
        // Saturday before Labor Day: Sunday and Monday closed.
        val next = AirCareHours.nextOpening(denver("2026-09-05T14:00"))
        assertEquals(LocalDateTime.parse("2026-09-08T08:00"), next.toLocalDateTime())
    }

    @Test
    fun `closing time is today's when open, none when closed`() {
        assertEquals(LocalDateTime.parse("2026-11-07T13:00"), AirCareHours.closingTime(denver("2026-11-07T09:00"))!!.toLocalDateTime())
        assertNull(AirCareHours.closingTime(denver("2026-11-08T09:00")))
    }

    @Test
    fun `hours follow Denver's clock across daylight saving`() {
        // 2026-11-01 is when Denver goes back to MST; 8 AM on the Monday after is 15:00 UTC.
        assertTrue(AirCareHours.isOpen(Instant.parse("2026-11-02T15:00:00Z")))
        assertFalse(AirCareHours.isOpen(Instant.parse("2026-11-02T14:59:59Z")))
    }
}
