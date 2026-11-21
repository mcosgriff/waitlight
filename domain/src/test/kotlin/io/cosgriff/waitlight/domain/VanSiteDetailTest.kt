package io.cosgriff.waitlight.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** A site's detail in the week of Friday 2026-11-20, with Thanksgiving week next. */
class VanSiteDetailTest {
    private fun denver(text: String): Instant = LocalDateTime.parse(text).atZone(AirCareHours.zone).toInstant()

    private val friday = LocalDate.of(2026, 11, 20)
    private val monday = LocalDate.of(2026, 11, 16)
    private val nextMonday = LocalDate.of(2026, 11, 23)
    private val seven = LocalTime.of(7, 0)
    private val five = LocalTime.of(17, 0)

    private fun site(
        description: String,
        days: Set<DayOfWeek> = setOf(FRIDAY),
        status: VanLiveStatus? = null,
        point: GeoPoint = GeoPoint(39.78313, -105.0598),
    ) = VanSite(description, point, days, seven, five, "Arvada", status)

    private val harlan = site("ON-RAMP TO EB I-70 FROM HARLAN ST.", status = VanLiveStatus.TESTING)
    private val pecos = site("ON-RAMP TO WB I-70 from PECOS ST.", status = VanLiveStatus.UNKNOWN, point = GeoPoint(39.78355, -105.0078))
    private val lcr = site("ON-RAMP TO NB I-25 from LCR-32", status = VanLiveStatus.NOT_TESTING, point = GeoPoint(40.48154, -104.9912))

    private val fetchedAt = denver("2026-11-20T16:52")

    /** Harlan on Monday and Friday this week and Monday to Wednesday next; Pecos and LCR-32 this Friday only. */
    private fun schedule(nextWeek: Boolean = true) = VanSchedule(
        listOf(
            VanTab(VanTabKind(VanWeek.CURRENT, VanTabDays.MON_THU), listOf(harlan.copy(days = setOf(MONDAY)))),
            VanTab(VanTabKind(VanWeek.CURRENT, VanTabDays.FRIDAY), listOf(harlan, pecos, lcr)),
            VanTab(
                VanTabKind(VanWeek.NEXT, VanTabDays.MON_THU),
                if (nextWeek) listOf(harlan.copy(days = setOf(MONDAY, TUESDAY, WEDNESDAY), status = VanLiveStatus.NOT_ON_SITE_UNSCHEDULED)) else null,
            ),
            VanTab(VanTabKind(VanWeek.NEXT, VanTabDays.FRIDAY), null, "An unexpected error occurred."),
        ),
        fetchedAt = fetchedAt,
    )

    private val mine = DriveBy(1, harlan.key, harlan.description, harlan.point, denver("2026-11-18T08:00"))
    private val later = mine.copy(id = 2, at = denver("2026-11-19T08:00"))
    private val elsewhere = DriveBy(3, pecos.key, pecos.description, pecos.point, denver("2026-11-19T09:00"))

    private fun detail(
        key: String,
        date: LocalDate,
        now: Instant = fetchedAt,
        schedule: VanSchedule = schedule(),
        outcome: FetchOutcome = FetchOutcome.OK,
    ): VanSiteDetail? {
        val list = vanList(VanTimes(schedule, outcome), now, null, { _, _ -> null }) { "Arvada" }
        return vanSiteDetail(list, schedule, key, date, now, driveByLog(listOf(mine, later, elsewhere), now))
    }

    @Test
    fun `today, while it's out, it's here until closing and says when it was checked`() {
        val detail = detail(harlan.key, friday)!!
        assertEquals(VanStatus.Reading, detail.row.status)
        assertTrue(detail.isToday)
        assertEquals(VanListState.LIVE, detail.state)
        assertEquals(VanDetailHours.Until(five), detail.hours)
        assertEquals(fetchedAt, detail.checkedAt)
        assertEquals(VanPin.READING, detail.pin)
    }

    @Test
    fun `done for today, it shows its hours`() {
        val detail = detail(harlan.key, friday, now = denver("2026-11-20T17:00"))!!
        assertEquals(VanStatus.Done, detail.row.status)
        assertEquals(VanDetailHours.Range(seven, five), detail.hours)
        // Still today, so still when it was checked.
        assertEquals(fetchedAt, detail.checkedAt)
        assertEquals(VanPin.OTHER, detail.pin)
    }

    @Test
    fun `another day shows its hours and no checked time`() {
        val detail = detail(harlan.key, nextMonday)!!
        assertFalse(detail.isToday)
        assertEquals(VanStatus.Scheduled, detail.row.status)
        assertEquals(VanDetailHours.Range(seven, five), detail.hours)
        assertNull(detail.checkedAt)
        assertEquals(VanPin.OTHER, detail.pin)
    }

    @Test
    fun `the pin goes by status`() {
        assertEquals(VanPin.SET_UP, detail(lcr.key, friday)!!.pin)
        assertEquals(VanPin.OTHER, detail(pecos.key, friday)!!.pin)
        // A page that can't be read shows every site as scheduled, the pin too.
        val broken = detail(harlan.key, friday, outcome = FetchOutcome.UNREADABLE)!!
        assertEquals(VanListState.BROKEN, broken.state)
        assertEquals(VanPin.OTHER, broken.pin)
    }

    @Test
    fun `the site's week, from every tab, today and past days marked`() {
        val week = vanSiteWeek(schedule(), harlan.key, fetchedAt)
        assertEquals(listOf(monday, friday, nextMonday, nextMonday.plusDays(1), nextMonday.plusDays(2)), week.visits.map { it.date })
        assertTrue(week.nextWeekPosted)
        assertEquals(
            listOf(
                VanWeekLine.Visit(monday, seven, five, isToday = false, isPast = true),
                VanWeekLine.Visit(friday, seven, five, isToday = true, isPast = false),
                VanWeekLine.Visit(nextMonday, seven, five, isToday = false, isPast = false),
                VanWeekLine.Visit(nextMonday.plusDays(1), seven, five, isToday = false, isPast = false),
                VanWeekLine.Visit(nextMonday.plusDays(2), seven, five, isToday = false, isPast = false),
            ),
            detail(harlan.key, friday)!!.week,
        )
        // The same week from another day's detail: today is still today.
        assertEquals(detail(harlan.key, friday)!!.week, detail(harlan.key, nextMonday)!!.week)
    }

    @Test
    fun `a site not on next week's tabs says so`() {
        assertEquals(
            listOf(VanWeekLine.Visit(friday, seven, five, isToday = true, isPast = false), VanWeekLine.NextWeekNotScheduled),
            detail(pecos.key, friday)!!.week,
        )
    }

    @Test
    fun `without next week's tabs it doesn't say it isn't scheduled`() {
        val week = detail(pecos.key, friday, schedule = schedule(nextWeek = false))!!.week
        assertEquals(listOf(VanWeekLine.Visit(friday, seven, five, isToday = true, isPast = false)), week)
    }

    @Test
    fun `on the weekend this week's days are past`() {
        val detail = detail(harlan.key, nextMonday, now = denver("2026-11-21T10:05"))!!
        assertEquals(VanListState.WEEKEND, detail.state)
        assertNull(detail.checkedAt)
        val visits = detail.week.filterIsInstance<VanWeekLine.Visit>()
        assertEquals(listOf(true, true, false, false, false), visits.map { it.isPast })
        assertTrue(visits.none { it.isToday })
    }

    @Test
    fun `only this site's drive-bys, newest first`() {
        assertEquals(listOf(later, mine), detail(harlan.key, friday)!!.driveBys)
        assertEquals(listOf(elsewhere), detail(pecos.key, friday)!!.driveBys)
    }

    @Test
    fun `a site not on the day has no detail`() {
        assertNull(detail(pecos.key, nextMonday))
        assertNull(detail("nowhere", friday))
        assertNull(detail(harlan.key, LocalDate.of(2026, 12, 7)))
    }
}
