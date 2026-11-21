package io.cosgriff.waitlight.data.vehicles

import io.cosgriff.waitlight.domain.AirCareHours
import io.cosgriff.waitlight.domain.ReminderKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.LocalDateTime
import java.time.YearMonth

class VehicleRepositoryTest {
    private val dir: File = Files.createTempDirectory("vehicles").toFile()
    private val file = File(dir, VehicleRepository.FILE_NAME)
    private val now: Instant = LocalDateTime.parse("2026-11-20T17:00").atZone(AirCareHours.zone).toInstant()

    private fun repository() = VehicleRepository({ file }, CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined)

    @After
    fun deleteFiles() {
        dir.deleteRecursively()
    }

    @Test
    fun `vehicles and their reminders survive a restart`() = runTest {
        val vehicles = repository()
        val gti = vehicles.add(" 2011 Volkswagen GTI ", YearMonth.of(2027, 7), remind = true, now)
        val fit = vehicles.add("2016 Honda Fit", YearMonth.of(2027, 3), remind = false, now)
        vehicles.setReminder(fit, ReminderKind.TEST_DUE, on = true, now)
        vehicles.markSent(mapOf(gti to setOf(ReminderKind.CLOSING_SOON)))

        val again = repository().vehicles.value
        assertEquals(listOf("2011 Volkswagen GTI", "2016 Honda Fit"), again.map { it.description })
        assertEquals(ReminderKind.DEFAULTS, again[0].reminders)
        // Opened May 1 and halfway on Oct 16, both before it was added: skipped, not sent late.
        assertEquals(setOf(ReminderKind.WINDOW_OPENS, ReminderKind.HALFWAY, ReminderKind.CLOSING_SOON), again[0].sent)
        assertEquals(setOf(ReminderKind.TEST_DUE), again[1].reminders)
    }

    @Test
    fun `a new renewal starts the reminders afresh, and removing keeps the rest`() = runTest {
        val vehicles = repository()
        val gti = vehicles.add("2011 Volkswagen GTI", YearMonth.of(2027, 7), remind = true, now)
        vehicles.add("2016 Honda Fit", YearMonth.of(2027, 3), remind = true, now)
        vehicles.update(gti, "GTI", YearMonth.of(2028, 7), now)
        val updated = repository().vehicles.value.first()
        assertEquals("GTI", updated.description)
        assertEquals(YearMonth.of(2028, 7), updated.renews)
        assertEquals(emptySet<ReminderKind>(), updated.sent)
        vehicles.remove(gti)
        assertEquals(listOf("2016 Honda Fit"), repository().vehicles.value.map { it.description })
    }

    @Test
    fun `an unreadable file is kept aside, not lost`() = runTest {
        file.writeText("not json")
        assertTrue(repository().loaded().isEmpty())
        assertEquals("not json", File(dir, "vehicles.json.bad").readText())
    }
}
