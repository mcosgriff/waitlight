package io.cosgriff.waitlight.data.vans

import io.cosgriff.waitlight.domain.FetchOutcome
import io.cosgriff.waitlight.domain.GeoPoint
import io.cosgriff.waitlight.domain.VanLiveStatus
import io.cosgriff.waitlight.domain.VanSchedule
import io.cosgriff.waitlight.domain.VanSite
import io.cosgriff.waitlight.domain.VanTab
import io.cosgriff.waitlight.domain.VanTabDays
import io.cosgriff.waitlight.domain.VanTabKind
import io.cosgriff.waitlight.domain.VanTimes
import io.cosgriff.waitlight.domain.VanWeek
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

class VanStoreTest {
    private val dir: File = Files.createTempDirectory("vans").toFile()
    private val file = File(dir, VanStore.FILE_NAME)
    private val store = VanStore({ file }, Dispatchers.Unconfined)

    @After
    fun deleteFiles() {
        dir.deleteRecursively()
    }

    @Test
    fun `saves and loads the schedule, its tabs and the outcome`() = runTest {
        val harlan = VanSite(
            "ON-RAMP TO EB I-70 FROM HARLAN ST.",
            GeoPoint(39.78313, -105.0598),
            setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
            LocalTime.of(8, 0),
            LocalTime.of(16, 30),
            "Wheat Ridge",
            VanLiveStatus.TESTING,
        )
        val times = VanTimes(
            VanSchedule(
                listOf(
                    VanTab(VanTabKind(VanWeek.CURRENT, VanTabDays.MON_THU), listOf(harlan)),
                    VanTab(VanTabKind(VanWeek.NEXT, VanTabDays.FRIDAY), null, "no pins"),
                ),
                Instant.ofEpochSecond(1_000),
            ),
            FetchOutcome.OFFLINE,
        )
        store.save(times)
        assertEquals(times, store.load())
    }

    @Test
    fun `an unreadable file is discarded`() = runTest {
        file.writeText("""{"lastOutcome":"OK","fetchedAt":1000,"tabs":[{"week":"CURRENT","days":"MON_THU","sites":[""")
        assertNull(store.load())
        assertEquals(emptyList<String>(), dir.list()!!.toList())
    }
}
