package io.cosgriff.waitlight.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class DriveBysTest {
    private fun denver(text: String): Instant = LocalDateTime.parse(text).atZone(AirCareHours.zone).toInstant()

    private val harlan = "39.78313,-105.05980|on-ramp to eb i-70 from harlan st"
    private fun driveBy(id: Long, at: String, key: String = harlan) =
        DriveBy(id, key, "ON-RAMP TO EB I-70 FROM HARLAN ST.", GeoPoint(39.78313, -105.0598), denver(at))

    @Test
    fun `newest first, counting the last 14 months`() {
        val now = denver("2026-11-20T17:00")
        val log = driveByLog(
            listOf(
                driveBy(1, "2025-09-19T08:00"),
                driveBy(2, "2025-09-20T08:00"),
                driveBy(3, "2026-11-20T07:15"),
                driveBy(4, "2026-03-02T16:40", key = "other"),
            ),
            now,
        )
        assertEquals(listOf(3L, 4L, 2L, 1L), log.entries.map { it.id })
        // 14 months back from Nov 20 is Sep 20, 2025: that day counts, the day before doesn't.
        assertEquals(3, log.inWindow)
        assertEquals(listOf(3L, 2L, 1L), log.at(harlan).map { it.id })
    }

    @Test
    fun `no drive-by in the future`() {
        val now = denver("2026-11-20T17:00")
        assertEquals(denver("2026-11-20T16:59"), driveByAt(LocalDate.of(2026, 11, 20), LocalTime.of(16, 59), now))
        assertEquals(now, driveByAt(LocalDate.of(2026, 11, 20), LocalTime.of(17, 0), now))
        assertNull(driveByAt(LocalDate.of(2026, 11, 20), LocalTime.of(17, 1), now))
        assertNull(driveByAt(LocalDate.of(2026, 11, 23), LocalTime.of(7, 0), now))
    }

    @Test
    fun `the entry keeps the site's name`() {
        assertEquals(VanName.Ramp("Harlan St", "EB I-70"), driveBy(1, "2026-11-20T07:15").name)
    }
}
