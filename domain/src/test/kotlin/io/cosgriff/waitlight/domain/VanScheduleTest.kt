package io.cosgriff.waitlight.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** The week of Friday 2026-11-20, with Thanksgiving week next. */
class VanScheduleTest {
    private fun denver(text: String): Instant = LocalDateTime.parse(text).atZone(AirCareHours.zone).toInstant()

    private val friday = LocalDate.of(2026, 11, 20)

    private fun site(
        description: String,
        days: Set<DayOfWeek> = setOf(FRIDAY),
        status: VanLiveStatus? = null,
        opens: LocalTime = LocalTime.of(7, 0),
        closes: LocalTime = LocalTime.of(17, 0),
        point: GeoPoint = GeoPoint(39.78313, -105.0598),
    ) = VanSite(description, point, days, opens, closes, "Arvada", status)

    private val harlan = site("ON-RAMP TO EB I-70 FROM HARLAN ST.", status = VanLiveStatus.TESTING)
    private val pecos = site("ON-RAMP TO WB I-70 from PECOS ST.", status = VanLiveStatus.UNKNOWN, point = GeoPoint(39.78355, -105.0078))
    private val lcr = site("ON-RAMP TO NB I-25 from LCR-32", status = VanLiveStatus.NOT_TESTING, point = GeoPoint(40.48154, -104.9912))
    private val havana = site("ON-RAMP TO EB I-70 from HAVANA ST.", days = setOf(MONDAY), closes = LocalTime.of(14, 30))

    private val schedule = VanSchedule(
        listOf(
            VanTab(VanTabKind(VanWeek.CURRENT, VanTabDays.MON_THU), listOf(havana.copy(days = setOf(MONDAY)))),
            VanTab(VanTabKind(VanWeek.CURRENT, VanTabDays.FRIDAY), listOf(harlan, pecos, lcr)),
            VanTab(
                VanTabKind(VanWeek.NEXT, VanTabDays.MON_THU),
                listOf(
                    harlan.copy(days = setOf(MONDAY, TUESDAY, WEDNESDAY), status = VanLiveStatus.NOT_ON_SITE_UNSCHEDULED),
                    pecos.copy(days = setOf(WEDNESDAY), status = VanLiveStatus.NOT_ON_SITE_UNSCHEDULED),
                ),
            ),
            VanTab(VanTabKind(VanWeek.NEXT, VanTabDays.FRIDAY), null, "An unexpected error occurred."),
        ),
        fetchedAt = denver("2026-11-20T16:52"),
    )

    /** Harlan 9 minutes, Pecos 12, LCR-32 none. */
    private val drives: (VanSite, GeoPoint) -> Int? = { site, _ ->
        when (site.key) {
            harlan.key -> 9
            pecos.key -> 12
            else -> null
        }
    }
    private val here = GeoPoint(39.8254, -105.0644)

    private fun list(now: Instant, outcome: FetchOutcome = FetchOutcome.OK, from: GeoPoint? = here) =
        vanList(VanTimes(schedule, outcome), now, from, drives) { "Arvada" }

    @Test
    fun `tab dates come from the clock, in Denver`() {
        assertEquals(LocalDate.of(2026, 11, 16), dateIn(VanWeek.CURRENT, MONDAY, friday))
        assertEquals(friday, dateIn(VanWeek.CURRENT, FRIDAY, friday))
        assertEquals(LocalDate.of(2026, 11, 26), dateIn(VanWeek.NEXT, THURSDAY, friday))
        // Saturday and Sunday are still the week just ended, until it's known when AirCare rolls the tabs over.
        assertEquals(LocalDate.of(2026, 11, 23), dateIn(VanWeek.NEXT, MONDAY, LocalDate.of(2026, 11, 22)))
        assertEquals(LocalDate.of(2026, 11, 16), currentVanWeek(LocalDate.of(2026, 11, 21)))
    }

    @Test
    fun `live statuses for today, while units are out`() {
        val now = denver("2026-11-20T16:52")
        assertEquals(VanStatus.Reading, vanStatus(harlan, friday, now, now))
        assertEquals(VanStatus.Paused, vanStatus(lcr, friday, now, now))
        assertEquals(VanStatus.OnSiteUnknown, vanStatus(pecos, friday, now, now))
        val due = site("x", status = VanLiveStatus.NOT_ON_SITE_SCHEDULED)
        assertEquals(VanStatus.NotSetUpYet, vanStatus(due, friday, now, now))
        val gone = site("x", status = VanLiveStatus.NOT_ON_SITE_UNSCHEDULED)
        assertEquals(VanStatus.Done, vanStatus(gone, friday, now, now))
    }

    @Test
    fun `before its hours a site starts later, after them it's done`() {
        val early = denver("2026-11-20T06:00")
        assertEquals(VanStatus.StartsAt(LocalTime.of(7, 0)), vanStatus(site("x", status = VanLiveStatus.NOT_ON_SITE_SCHEDULED), friday, early, early))
        assertEquals(VanStatus.StartsAt(LocalTime.of(6, 35)), vanStatus(site("x", opens = LocalTime.of(6, 35)), friday, early, early))
        val late = denver("2026-11-20T17:00")
        assertEquals(VanStatus.Done, vanStatus(harlan, friday, late, late))
    }

    @Test
    fun `a live status older than 30 minutes is just scheduled`() {
        val fetched = denver("2026-11-20T12:00")
        assertEquals(VanStatus.Reading, vanStatus(harlan, friday, fetched.plus(Duration.ofMinutes(30)), fetched))
        assertEquals(VanStatus.Scheduled, vanStatus(harlan, friday, fetched.plus(Duration.ofMinutes(31)), fetched))
        assertEquals(VanStatus.Scheduled, vanStatus(harlan, friday, fetched, fetched, liveUsable = false))
    }

    @Test
    fun `another day is scheduled`() {
        val now = denver("2026-11-20T12:00")
        assertEquals(VanStatus.Scheduled, vanStatus(harlan, LocalDate.of(2026, 11, 23), now, now))
    }

    @Test
    fun `days run from today to the end of next week, nearest first by drive`() {
        val view = list(denver("2026-11-20T16:52"))
        assertEquals(VanListState.LIVE, view.state)
        assertEquals((0..7L).map { friday.plusDays(it) }.filter { it.dayOfWeek.value <= 5 }, view.days.map { it.date })
        val today = view.days.first()
        assertTrue(today.isToday)
        val rows = (today.content as VanDayContent.Sites).rows
        // LCR-32 has no drive time: after the rest, never by straight-line distance.
        assertEquals(listOf(harlan.key, pecos.key, lcr.key), rows.map { it.site.key })
        assertEquals(listOf(9, 12, null), rows.map { it.driveMinutes })
        assertEquals(harlan.key, nearestReading(today)!!.site.key)
        assertEquals(1, readingCount(today))
    }

    @Test
    fun `a day with no sites, and one not posted`() {
        val days = list(denver("2026-11-20T16:52")).days
        val thanksgiving = days.first { it.date == LocalDate.of(2026, 11, 26) }
        assertEquals(VanDayContent.NoSites(Holiday.THANKSGIVING), thanksgiving.content)
        assertEquals(VanDayContent.NotPosted, days.first { it.date == LocalDate.of(2026, 11, 27) }.content)
        val wednesday = days.first { it.date == LocalDate.of(2026, 11, 25) }.content as VanDayContent.Sites
        assertEquals(listOf(VanStatus.Scheduled, VanStatus.Scheduled), wednesday.rows.map { it.status })
        assertNull(nearestReading(days.first { it.date == LocalDate.of(2026, 11, 23) }))
    }

    @Test
    fun `on the weekend no units are out, and next week shows from Monday`() {
        val view = list(denver("2026-11-21T10:05"))
        assertEquals(VanListState.WEEKEND, view.state)
        assertEquals(LocalDate.of(2026, 11, 23), view.days.first().date)
        assertEquals(5, view.days.size)
        assertFalse(view.days.any { it.isToday })
    }

    @Test
    fun `an unreadable page keeps the schedule and shows every site as scheduled`() {
        val view = list(denver("2026-11-20T12:00"), FetchOutcome.UNREADABLE)
        assertEquals(VanListState.BROKEN, view.state)
        val rows = (view.days.first().content as VanDayContent.Sites).rows
        assertTrue(rows.all { it.status == VanStatus.Scheduled })
        assertNull(nearestReading(view.days.first()))
    }

    @Test
    fun `a failed fetch goes stale once the statuses are too old`() {
        assertEquals(VanListState.LIVE, list(denver("2026-11-20T17:10"), FetchOutcome.FAILED).state)
        assertEquals(VanListState.STALE, list(denver("2026-11-20T17:30"), FetchOutcome.OFFLINE).state)
        assertEquals(VanListState.EMPTY, vanListState(VanTimes(), denver("2026-11-20T12:00")))
    }

    @Test
    fun `without a location nothing has a drive time and the list goes by name`() {
        val rows = (list(denver("2026-11-20T16:52"), from = null).days.first().content as VanDayContent.Sites).rows
        assertTrue(rows.all { it.driveMinutes == null })
        assertEquals(listOf(harlan, lcr, pecos).map { it.key }, rows.map { it.site.key })
        assertNull(nearestReading(list(denver("2026-11-20T16:52"), from = null).days.first()))
    }

    @Test
    fun `the site's week, from every tab`() {
        val week = vanSiteWeek(schedule, harlan.key, denver("2026-11-20T16:52"))
        assertEquals(
            listOf(friday, LocalDate.of(2026, 11, 23), LocalDate.of(2026, 11, 24), LocalDate.of(2026, 11, 25)),
            week.visits.map { it.date },
        )
        assertTrue(week.nextWeekPosted)
    }

    @Test
    fun `fetching the van page`() {
        val weekday = denver("2026-11-20T12:00")
        assertTrue(vanWorthFetching(FetchReason.LAUNCH, denver("2026-11-21T23:00"), weekday))
        // The app starting on the vans tab is one launch, not two.
        assertFalse(vanWorthFetching(FetchReason.LAUNCH, weekday.plusSeconds(59), weekday))
        assertTrue(vanWorthFetching(FetchReason.DUE, weekday, weekday.minus(Duration.ofMinutes(15))))
        assertFalse(vanWorthFetching(FetchReason.DUE, weekday, weekday.minus(Duration.ofMinutes(14))))
        assertTrue(vanWorthFetching(FetchReason.DUE, weekday, null))
        // Units are out on weekdays from 5:30 AM to 7 PM only.
        assertFalse(vanWorthFetching(FetchReason.DUE, denver("2026-11-20T19:00"), null))
        assertTrue(vanWorthFetching(FetchReason.DUE, denver("2026-11-20T05:30"), null))
        assertFalse(vanWorthFetching(FetchReason.DUE, denver("2026-11-21T12:00"), null))
        assertFalse(vanWorthFetching(FetchReason.MANUAL, denver("2026-11-21T12:00"), null))
        assertTrue(vanWorthFetching(FetchReason.MANUAL, weekday, weekday))
    }
}
