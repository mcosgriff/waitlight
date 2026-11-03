package io.cosgriff.waitlight.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime

class FreshnessTest {
    private fun denver(text: String): Instant = LocalDateTime.parse(text).atZone(AirCareHours.zone).toInstant()

    private val now = denver("2026-11-02T14:18")

    @Test
    fun `live under 15 minutes old, due from 15`() {
        assertEquals(ListState.LIVE, listState(now, denver("2026-11-02T14:04"), FetchOutcome.OK))
        assertEquals(ListState.DUE, listState(now, denver("2026-11-02T14:03"), FetchOutcome.OK))
    }

    @Test
    fun `due before the first fetch`() {
        assertEquals(ListState.DUE, listState(now, null, FetchOutcome.OK))
    }

    @Test
    fun `closed outside hours, whatever the fetch did`() {
        val evening = denver("2026-11-02T18:00")
        assertEquals(ListState.CLOSED, listState(evening, denver("2026-11-02T17:29"), FetchOutcome.UNREADABLE))
    }

    @Test
    fun `a failed fetch with fresh data changes nothing`() {
        assertEquals(ListState.LIVE, listState(now, denver("2026-11-02T14:10"), FetchOutcome.FAILED))
        assertEquals(ListState.DUE, listState(now, denver("2026-11-02T14:00"), FetchOutcome.OFFLINE))
    }

    @Test
    fun `a failed fetch with data over 30 minutes old is stale or offline`() {
        val old = denver("2026-11-02T13:30")
        assertEquals(ListState.STALE, listState(now, old, FetchOutcome.FAILED))
        assertEquals(ListState.OFFLINE, listState(now, old, FetchOutcome.OFFLINE))
        assertEquals(ListState.OFFLINE, listState(now, null, FetchOutcome.OFFLINE))
    }

    @Test
    fun `an unreadable page is broken even with fresh data`() {
        assertEquals(ListState.BROKEN, listState(now, denver("2026-11-02T14:10"), FetchOutcome.UNREADABLE))
    }

    @Test
    fun `refresh is disabled with a countdown while live`() {
        val newest = denver("2026-11-02T14:14")
        assertEquals(RefreshButton(enabled = false, emphasized = false, nextUpdateInMinutes = 11), refreshButton(ListState.LIVE, now, newest))
        // 30 seconds to go still reads "1 min", never 0.
        val almost = denver("2026-11-02T14:28:30")
        assertEquals(1, refreshButton(ListState.LIVE, almost, newest).nextUpdateInMinutes)
    }

    @Test
    fun `a phone clock behind AirCare's reads live with a countdown of at most 15 minutes`() {
        // AirCare says 14:40 while the phone says 14:18: the next update reads 37 minutes away, uncapped.
        val ahead = denver("2026-11-02T14:40")
        assertEquals(ListState.LIVE, listState(now, ahead, FetchOutcome.OK))
        assertEquals(15, refreshButton(ListState.LIVE, now, ahead).nextUpdateInMinutes)
    }

    @Test
    fun `refresh is filled when due, plain as a retry, off while closed`() {
        assertEquals(RefreshButton(true, true, null), refreshButton(ListState.DUE, now, null))
        for (state in listOf(ListState.STALE, ListState.OFFLINE, ListState.BROKEN)) {
            assertEquals(RefreshButton(true, false, null), refreshButton(state, now, null))
        }
        assertEquals(RefreshButton(false, false, null), refreshButton(ListState.CLOSED, now, null))
    }

    @Test
    fun `never fetch outside opening hours`() {
        assertFalse(shouldFetch(denver("2026-11-08T10:00"), null))
        assertFalse(shouldFetch(denver("2026-11-02T07:59"), null))
    }

    @Test
    fun `fetch when open and the data is due`() {
        assertTrue(shouldFetch(now, null))
        assertTrue(shouldFetch(now, denver("2026-11-02T14:03")))
        assertFalse(shouldFetch(now, denver("2026-11-02T14:04")))
    }

    @Test
    fun `a launch always fetches, open or closed, fresh or not`() {
        assertTrue(worthFetching(FetchReason.LAUNCH, denver("2026-11-08T10:00"), null))
        assertTrue(worthFetching(FetchReason.LAUNCH, denver("2026-11-02T19:50"), denver("2026-11-02T17:29")))
        assertTrue(worthFetching(FetchReason.LAUNCH, now, denver("2026-11-02T14:10")))
    }

    @Test
    fun `a tap on refresh fetches whenever open, and never while closed`() {
        assertTrue(worthFetching(FetchReason.MANUAL, now, denver("2026-11-02T14:10")))
        assertFalse(worthFetching(FetchReason.MANUAL, denver("2026-11-08T10:00"), null))
    }

    @Test
    fun `an unprompted check goes by shouldFetch`() {
        assertTrue(worthFetching(FetchReason.DUE, now, denver("2026-11-02T14:03")))
        assertFalse(worthFetching(FetchReason.DUE, now, denver("2026-11-02T14:04")))
        assertFalse(worthFetching(FetchReason.DUE, denver("2026-11-08T10:00"), null))
    }
}
