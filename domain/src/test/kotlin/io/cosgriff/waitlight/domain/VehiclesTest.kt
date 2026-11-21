package io.cosgriff.waitlight.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit

class VehiclesTest {
    private fun denver(text: String): Instant = LocalDateTime.parse(text).atZone(AirCareHours.zone).toInstant()
    private fun date(text: String): LocalDate = LocalDate.parse(text)

    private val july2027 = YearMonth.of(2027, 7)
    private val march2027 = YearMonth.of(2027, 3)
    private val gti = Vehicle(1, "2011 Volkswagen GTI", july2027, ReminderKind.DEFAULTS)
    private val fit = Vehicle(2, "2016 Honda Fit", march2027, ReminderKind.DEFAULTS)

    private fun driveBy(id: Long, at: String, vehicleId: Long? = null) =
        DriveBy(id, "k", "ON-RAMP TO EB I-70 FROM HARLAN ST.", GeoPoint(39.78313, -105.0598), denver(at), vehicleId)

    @Test
    fun `July's window is May 1 the year before to April 1`() {
        val window = RapidScreenWindow.of(july2027)
        assertEquals(date("2026-05-01"), window.opens)
        assertEquals(date("2027-04-01"), window.closes)
        assertEquals(date("2027-08-31"), window.testDueBy)
    }

    @Test
    fun `March's window crosses the year`() {
        val window = RapidScreenWindow.of(march2027)
        assertEquals(date("2026-01-01"), window.opens)
        assertEquals(date("2026-12-01"), window.closes)
        assertEquals(date("2027-04-30"), window.testDueBy)
        // January: opens in November two years back, closes in October.
        val january = RapidScreenWindow.of(YearMonth.of(2027, 1))
        assertEquals(date("2025-11-01"), january.opens)
        assertEquals(date("2026-10-01"), january.closes)
    }

    @Test
    fun `the window closes at least 90 days before the renewal month, never later than the 60-day page says`() {
        var month = YearMonth.of(2026, 1)
        while (month <= YearMonth.of(2032, 12)) {
            val window = RapidScreenWindow.of(month)
            val before = ChronoUnit.DAYS.between(window.closes, month.atDay(1))
            assertTrue("$month closes ${window.closes}, $before days before", before >= 90)
            // Never more than a few days stricter than the van page's "first of the month, three months before".
            assertTrue("$month closes ${window.closes}", before <= 92)
            assertTrue(window.closes.isBefore(month.atDay(1).minusDays(60)))
            month = month.plusMonths(1)
        }
    }

    @Test
    fun `a May renewal closes on Jan 31 when February is short, Feb 1 in a leap year`() {
        // Feb 1 to May 1 is 89 days in 2027: too late by the 90-day rule.
        assertEquals(date("2027-01-31"), RapidScreenWindow.of(YearMonth.of(2027, 5)).closes)
        assertEquals(date("2028-02-01"), RapidScreenWindow.of(YearMonth.of(2028, 5)).closes)
    }

    @Test
    fun `the test is due at the end of the month after renewal`() {
        assertEquals(date("2028-01-31"), RapidScreenWindow.of(YearMonth.of(2027, 12)).testDueBy)
        assertEquals(date("2028-02-29"), RapidScreenWindow.of(YearMonth.of(2028, 1)).testDueBy)
    }

    @Test
    fun `where today falls in the window`() {
        val window = RapidScreenWindow.of(july2027)
        val open = window.stateOn(date("2026-11-20")) as WindowState.Open
        assertEquals(132, open.daysLeft)
        assertEquals(0.606f, open.fraction, 0.001f)
        assertFalse(open.closingSoon)
        assertEquals(WindowState.Before(1), window.stateOn(date("2026-04-30")))
        assertEquals(WindowState.Open(335, 0f), window.stateOn(date("2026-05-01")))
        assertEquals(WindowState.Open(0, 1f), window.stateOn(date("2027-04-01")))
        assertEquals(WindowState.Closed, window.stateOn(date("2027-04-02")))
        assertEquals(WindowState.Closed, window.stateOn(date("2027-08-31")))
        assertEquals(WindowState.Past(YearMonth.of(2029, 7)), window.stateOn(date("2027-09-01")))
        assertEquals(WindowState.Past(YearMonth.of(2031, 7)), window.stateOn(date("2029-09-01")))
        val fit = RapidScreenWindow.of(march2027).stateOn(date("2026-11-20")) as WindowState.Open
        assertEquals(11, fit.daysLeft)
        assertTrue(fit.closingSoon)
    }

    @Test
    fun `time left in months, then days`() {
        val window = RapidScreenWindow.of(july2027)
        assertEquals(TimeLeft.Months(5), window.timeLeft(date("2026-10-16")))
        assertEquals(TimeLeft.Months(1), window.timeLeft(date("2027-03-01")))
        assertEquals(TimeLeft.Days(12), window.timeLeft(date("2027-03-20")))
    }

    @Test
    fun `drive-bys count for their vehicle, inside its window, in Denver`() {
        val entries = listOf(
            driveBy(1, "2026-11-20T07:15", vehicleId = 1),
            driveBy(2, "2026-04-30T23:30", vehicleId = 1), // the day before the GTI's window
            driveBy(3, "2026-05-01T00:10", vehicleId = 1),
            driveBy(4, "2026-03-02T16:40", vehicleId = 2),
            driveBy(5, "2026-06-01T08:00"), // no vehicle: counted only while there's one
            driveBy(6, "2026-11-21T08:00", vehicleId = 1), // after now
        )
        val now = denver("2026-11-20T17:00")
        val (gtiWindow, fitWindow) = vehicleWindows(listOf(fit, gti), entries, now)
        assertEquals(listOf(1L, 3L), gtiWindow.driveBys.map { it.id })
        assertEquals(listOf(4L), fitWindow.driveBys.map { it.id })
        assertEquals(listOf(1L, 5L, 3L), vehicleWindows(listOf(gti), entries, now).single().driveBys.map { it.id })
    }

    @Test
    fun `the first choice is an open window`() {
        val now = denver("2026-11-20T17:00")
        val later = Vehicle(3, "2020 Ford F-150", YearMonth.of(2029, 1))
        assertEquals(1L, firstChoice(vehicleWindows(listOf(later, gti), emptyList(), now))?.vehicle?.id)
        assertEquals(3L, firstChoice(vehicleWindows(listOf(later), emptyList(), now))?.vehicle?.id)
        assertNull(firstChoice(emptyList()))
    }

    @Test
    fun `reminder days`() {
        val gtiWindow = RapidScreenWindow.of(july2027)
        assertEquals(date("2026-05-01"), gtiWindow.reminderDate(ReminderKind.WINDOW_OPENS))
        assertEquals(date("2026-10-16"), gtiWindow.reminderDate(ReminderKind.HALFWAY))
        assertEquals(date("2027-03-18"), gtiWindow.reminderDate(ReminderKind.CLOSING_SOON))
        assertEquals(date("2027-07-01"), gtiWindow.reminderDate(ReminderKind.TEST_DUE))
        val fitWindow = RapidScreenWindow.of(march2027)
        assertEquals(date("2026-11-17"), fitWindow.reminderDate(ReminderKind.CLOSING_SOON))
        assertEquals(date("2027-03-01"), fitWindow.reminderDate(ReminderKind.TEST_DUE))
        assertEquals(denver("2026-11-17T09:00"), fitWindow.reminderAt(ReminderKind.CLOSING_SOON))
    }

    @Test
    fun `a reminder goes out once, when its time comes`() {
        // Added on Nov 1, after the window opened and its halfway day.
        val fit = fit.settled(denver("2026-11-01T12:00"))
        val before = denver("2026-11-17T08:59")
        val at = denver("2026-11-17T09:00")
        assertTrue(reminderRun(listOf(fit), emptyList(), before).settled.isEmpty())
        val run = reminderRun(listOf(fit), emptyList(), at)
        assertEquals(listOf(ReminderKind.CLOSING_SOON), run.post.map { it.kind })
        assertEquals(setOf(ReminderKind.CLOSING_SOON), run.settled[2])
        val sent = fit.copy(sent = fit.sent + run.settled.getValue(2))
        assertTrue(reminderRun(listOf(sent), emptyList(), at).settled.isEmpty())
        assertEquals(sent.window.reminderAt(ReminderKind.TEST_DUE), nextReminderAt(listOf(sent.copy(reminders = ReminderKind.entries.toSet()))))
        assertNull(nextReminderAt(listOf(sent)))
    }

    @Test
    fun `halfway only with fewer than two drive-bys`() {
        val now = denver("2026-10-16T09:00")
        val gti = gti.copy(sent = setOf(ReminderKind.WINDOW_OPENS))
        assertEquals(listOf(ReminderKind.HALFWAY), reminderRun(listOf(gti), listOf(driveBy(1, "2026-06-01T08:00", 1)), now).post.map { it.kind })
        val two = listOf(driveBy(1, "2026-06-01T08:00", 1), driveBy(2, "2026-07-01T08:00", 1))
        val run = reminderRun(listOf(gti), two, now)
        assertTrue(run.post.isEmpty())
        assertEquals(setOf(ReminderKind.HALFWAY), run.settled[1])
    }

    @Test
    fun `switched off, or no use any more, it doesn't go out`() {
        val off = gti.copy(reminders = emptySet())
        assertTrue(reminderRun(listOf(off), emptyList(), denver("2026-05-01T09:00")).settled.isEmpty())
        // The phone was off from before the closing reminder until after the window closed.
        val late = gti.copy(sent = setOf(ReminderKind.WINDOW_OPENS, ReminderKind.HALFWAY))
        val run = reminderRun(listOf(late), emptyList(), denver("2027-04-02T10:00"))
        assertTrue(run.post.isEmpty())
        assertEquals(setOf(ReminderKind.CLOSING_SOON), run.settled[1])
    }

    @Test
    fun `the test reminder runs to the due date, across the year`() {
        val december = Vehicle(3, "2009 Subaru Outback", YearMonth.of(2027, 12), setOf(ReminderKind.TEST_DUE))
        assertEquals(denver("2027-12-01T09:00"), nextReminderAt(listOf(december)))
        assertEquals(listOf(ReminderKind.TEST_DUE), reminderRun(listOf(december), emptyList(), denver("2028-01-31T20:00")).post.map { it.kind })
        assertTrue(reminderRun(listOf(december), emptyList(), denver("2028-02-01T00:00")).post.isEmpty())
    }

    @Test
    fun `a new vehicle, a changed renewal or a reminder switched on doesn't send what's already past`() {
        val now = denver("2026-11-20T17:00")
        val added = gti.settled(now)
        assertEquals(setOf(ReminderKind.WINDOW_OPENS, ReminderKind.HALFWAY), added.sent - setOf(ReminderKind.TEST_DUE, ReminderKind.CLOSING_SOON))
        assertEquals(added.window.reminderAt(ReminderKind.CLOSING_SOON), nextReminderAt(listOf(added)))
        val moved = added.renewing(YearMonth.of(2028, 7), now)
        assertEquals(emptySet<ReminderKind>(), moved.sent)
        assertEquals(added, added.renewing(july2027, now))
        val due = added.withReminder(ReminderKind.TEST_DUE, true, now)
        assertTrue(ReminderKind.TEST_DUE in due.reminders)
        assertFalse(ReminderKind.TEST_DUE in due.sent)
        val halfway = gti.copy(reminders = emptySet()).withReminder(ReminderKind.HALFWAY, true, now)
        assertEquals(setOf(ReminderKind.HALFWAY), halfway.sent)
        assertEquals(emptySet<ReminderKind>(), halfway.withReminder(ReminderKind.HALFWAY, false, now).reminders)
    }

    @Test
    fun `when several are due for one vehicle, only the latest goes out`() {
        val run = reminderRun(listOf(fit), emptyList(), denver("2026-11-18T09:00"))
        assertEquals(listOf(ReminderKind.CLOSING_SOON), run.post.map { it.kind })
    }

    @Test
    fun `short names`() {
        assertEquals("GTI", gti.shortName)
        assertEquals("Fit", fit.shortName)
        assertEquals("Land Cruiser", VehicleRules.shortName("2018 Toyota  Land Cruiser"))
        assertEquals("2018 Tacoma", VehicleRules.shortName(" 2018 Tacoma "))
        assertEquals("The blue car", VehicleRules.shortName("The blue car"))
    }
}
