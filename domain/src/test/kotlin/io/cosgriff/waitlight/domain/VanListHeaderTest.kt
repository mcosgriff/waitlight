package io.cosgriff.waitlight.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** The vans list header's decisions, Friday 2026-11-20 in Denver. */
class VanListHeaderTest {
    private fun denver(text: String): Instant = LocalDateTime.parse(text).atZone(AirCareHours.zone).toInstant()

    private val fetched = denver("2026-11-20T16:52")
    private val friday = LocalDate.of(2026, 11, 20)
    private val monday = LocalDate.of(2026, 11, 23)
    private val thursday = LocalDate.of(2026, 11, 26)
    private val arvada = ZipPlace("80003", GeoPoint(39.8254, -105.0644), "Arvada")

    private fun site(description: String, lat: Double) =
        VanSite(description, GeoPoint(lat, -105.0598), setOf(FRIDAY, MONDAY), LocalTime.of(7, 0), LocalTime.of(17, 0), "Arvada", null)

    private val harlan = site("ON-RAMP TO EB I-70 FROM HARLAN ST.", 39.78313)
    private val pecos = site("ON-RAMP TO WB I-70 from PECOS ST.", 39.78355)
    private val sheridan = site("ON-RAMP to SB US-285 from SHERIDAN blvd", 39.65369)

    private fun row(site: VanSite, status: VanStatus, drive: Int?, date: LocalDate = friday) = VanRow(site, date, status, "Arvada", drive?.let { VanDrive.Plain(it) })

    private val harlanReading = row(harlan, VanStatus.Reading, 9)
    private val pecosReading = row(pecos, VanStatus.Reading, 12)
    private val sheridanDone = row(sheridan, VanStatus.Done, 5)

    private fun today(vararg rows: VanRow) = VanDay(friday, true, VanDayContent.Sites(rows.toList()))

    private val nextMonday = VanDay(monday, false, VanDayContent.Sites(listOf(row(harlan, VanStatus.Scheduled, 9, monday))))
    private val thanksgiving = VanDay(thursday, false, VanDayContent.NoSites(Holiday.THANKSGIVING))

    private fun list(state: VanListState = VanListState.LIVE, vararg days: VanDay = arrayOf(today(sheridanDone, harlanReading, pecosReading), nextMonday, thanksgiving)) =
        VanList(state, days.toList(), fetched)

    private fun header(
        list: VanList = list(),
        selected: LocalDate? = null,
        readingOnly: Boolean = false,
        here: Here? = Here.Zip(arvada),
        allowed: Boolean = false,
        refreshing: Boolean = false,
    ) = vanListHeader(list, selected, readingOnly, here, allowed, refreshing)

    @Test
    fun `the place line is the wait list's - a typed ZIP, the fix, or location off`() {
        assertEquals(PlaceLine.Zip(arvada), header().place)
        assertEquals(PlaceLine.Device(arvada), header(here = Here.Device(GeoPoint(39.83, -105.06), arvada), allowed = true).place)
        assertEquals(PlaceLine.Finding, header(here = null, allowed = true).place)
        assertEquals(PlaceLine.LocationOff, header(here = null).place)
    }

    @Test
    fun `the checked line - status, schedule, or how old the schedule is`() {
        assertEquals(VanCheckedLine.StatusChecked(fetched), header().checked)
        assertEquals(VanCheckedLine.ScheduleChecked(fetched), header(list(VanListState.WEEKEND)).checked)
        assertEquals(VanCheckedLine.ScheduleFrom(fetched), header(list(VanListState.STALE)).checked)
        assertEquals(VanCheckedLine.ScheduleFrom(fetched), header(list(VanListState.BROKEN)).checked)
        val empty = VanList(VanListState.EMPTY, emptyList(), null)
        assertEquals(VanCheckedLine.Getting, header(empty, refreshing = true).checked)
        assertNull(header(empty).checked)
    }

    @Test
    fun `the banner - weekend, stale and broken, with when the schedule is from`() {
        assertNull(header().banner)
        assertEquals(VanBanner.Weekend, header(list(VanListState.WEEKEND)).banner)
        assertEquals(VanBanner.Stale(fetched), header(list(VanListState.STALE)).banner)
        assertEquals(VanBanner.Broken(fetched), header(list(VanListState.BROKEN)).banner)
        assertNull(header(VanList(VanListState.EMPTY, emptyList(), null)).banner)
    }

    @Test
    fun `retry only where it helps - a failed fetch or no schedule yet`() {
        assertTrue(header(list(VanListState.STALE)).retry)
        assertTrue(header(VanList(VanListState.EMPTY, emptyList(), null)).retry)
        assertFalse(header().retry)
        assertFalse(header(list(VanListState.WEEKEND)).retry)
        assertFalse(header(list(VanListState.BROKEN)).retry)
    }

    @Test
    fun `the day on screen - the one picked, else the first`() {
        assertEquals(friday, header().day?.date)
        assertEquals(monday, header(selected = monday).day?.date)
        assertEquals(friday, header(selected = LocalDate.of(2026, 12, 25)).day?.date)
        assertNull(header(VanList(VanListState.EMPTY, emptyList(), null)).day)
    }

    @Test
    fun `the summary line`() {
        assertEquals(VanSummary.Today(sites = 3, reading = 2), header().summary)
        assertEquals(VanSummary.TodayUnavailable(sites = 3), header(list(VanListState.BROKEN)).summary)
        assertEquals(VanSummary.ReadingOnly(reading = 2, sites = 3), header(readingOnly = true).summary)
        assertEquals(VanSummary.Day(monday, sites = 1), header(selected = monday).summary)
        assertEquals(VanSummary.Date(thursday), header(selected = thursday).summary)
        assertNull(header(VanList(VanListState.EMPTY, emptyList(), null)).summary)
    }

    @Test
    fun `the reading filter - today only, not when broken, while something reads or it is on`() {
        assertEquals(ReadingFilter(shown = true, selected = false), header().filter)
        assertEquals(ReadingFilter(shown = true, selected = true), header(readingOnly = true).filter)
        assertEquals(ReadingFilter(shown = false, selected = false), header(selected = monday, readingOnly = true).filter)
        assertEquals(ReadingFilter(shown = false, selected = false), header(list(VanListState.BROKEN), readingOnly = true).filter)
        val noneReading = list(VanListState.LIVE, today(sheridanDone))
        assertEquals(ReadingFilter(shown = false, selected = false), header(noneReading).filter)
        // On with nothing reading: still shown, to turn it back off.
        assertEquals(ReadingFilter(shown = true, selected = true), header(noneReading, readingOnly = true).filter)
    }

    @Test
    fun `the filter keeps the reading rows`() {
        assertEquals(listOf(sheridanDone, harlanReading, pecosReading), header().rows.map { it.row })
        assertEquals(listOf(harlanReading, pecosReading), header(readingOnly = true).rows.map { it.row })
        // Another day: the filter doesn't apply.
        assertEquals(1, header(selected = monday, readingOnly = true).rows.size)
        assertTrue(header(selected = thursday).rows.isEmpty())
    }

    @Test
    fun `the hero - nearest reading, only while live`() {
        assertEquals(harlanReading, header().hero)
        assertNull(header(selected = monday).hero)
        assertNull(header(list(VanListState.STALE)).hero)
        assertNull(header(list(VanListState.BROKEN)).hero)
        assertNull(header(list(VanListState.LIVE, today(sheridanDone))).hero)
    }

    @Test
    fun `each row's status kind`() {
        assertEquals(VanRowStatus.Plain(VanStatus.Reading), header().rows[1].status)
        assertEquals(VanRowStatus.Plain(VanStatus.Scheduled), vanRowStatus(row(harlan, VanStatus.Scheduled, 9), VanListState.LIVE, isToday = true))
        assertEquals(VanRowStatus.ScheduledToday, vanRowStatus(row(harlan, VanStatus.Scheduled, 9), VanListState.BROKEN, isToday = true))
        assertEquals(VanRowStatus.ScheduledOn(monday), header(selected = monday).rows.single().status)
        assertEquals(VanRowStatus.Plain(VanStatus.Done), vanRowStatus(sheridanDone, VanListState.BROKEN, isToday = true))
    }
}
