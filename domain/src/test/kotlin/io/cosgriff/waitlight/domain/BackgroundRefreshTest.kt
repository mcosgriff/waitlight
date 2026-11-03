package io.cosgriff.waitlight.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime

class BackgroundRefreshTest {
    private fun denver(text: String): Instant = LocalDateTime.parse(text).atZone(AirCareHours.zone).toInstant()

    @Test
    fun `polls while open`() {
        // Monday 2026-11-02.
        assertEquals(BackgroundPlan.Poll, backgroundPlan(denver("2026-11-02T08:00")))
        assertEquals(BackgroundPlan.Poll, backgroundPlan(denver("2026-11-02T14:18")))
        assertEquals(BackgroundPlan.Poll, backgroundPlan(denver("2026-11-02T17:14:59")))
    }

    @Test
    fun `sleeps when the next check would find them closed`() {
        val tomorrow = BackgroundPlan.SleepUntil(denver("2026-11-03T08:00"))
        assertEquals(tomorrow, backgroundPlan(denver("2026-11-02T17:15")))
        assertEquals(tomorrow, backgroundPlan(denver("2026-11-02T17:29")))
    }

    @Test
    fun `sleeps overnight until the morning`() {
        val morning = BackgroundPlan.SleepUntil(denver("2026-11-03T08:00"))
        assertEquals(morning, backgroundPlan(denver("2026-11-02T17:30")))
        assertEquals(morning, backgroundPlan(denver("2026-11-02T23:00")))
        assertEquals(morning, backgroundPlan(denver("2026-11-03T07:59")))
    }

    @Test
    fun `sleeps through Saturday afternoon and Sunday until Monday`() {
        val monday = BackgroundPlan.SleepUntil(denver("2026-11-09T08:00"))
        assertEquals(BackgroundPlan.Poll, backgroundPlan(denver("2026-11-07T12:00")))
        assertEquals(monday, backgroundPlan(denver("2026-11-07T12:50")))
        assertEquals(monday, backgroundPlan(denver("2026-11-08T10:00")))
    }

    @Test
    fun `sleeps through a holiday`() {
        // Thanksgiving, Thursday 2026-11-26: closed all day, open again Friday.
        val friday = BackgroundPlan.SleepUntil(denver("2026-11-27T08:00"))
        assertEquals(friday, backgroundPlan(denver("2026-11-25T18:00")))
        assertEquals(friday, backgroundPlan(denver("2026-11-26T10:00")))
    }
}
