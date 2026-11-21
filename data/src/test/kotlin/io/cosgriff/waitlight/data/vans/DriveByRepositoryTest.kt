package io.cosgriff.waitlight.data.vans

import io.cosgriff.waitlight.domain.GeoPoint
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

class DriveByRepositoryTest {
    private val dir: File = Files.createTempDirectory("driveBys").toFile()
    private val file = File(dir, DriveByRepository.FILE_NAME)

    private fun repository() = DriveByRepository({ file }, CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined)

    @After
    fun deleteFiles() {
        dir.deleteRecursively()
    }

    @Test
    fun `entries survive a restart, and can be removed`() = runTest {
        val log = repository()
        log.add("k|harlan", "ON-RAMP TO EB I-70 FROM HARLAN ST.", GeoPoint(39.78313, -105.0598), Instant.ofEpochSecond(1_000))
        log.add("k|pecos", "ON-RAMP TO WB I-70 from PECOS ST.", GeoPoint(39.78355, -105.0078), Instant.ofEpochSecond(2_000))
        assertEquals(listOf(1L, 2L), repository().driveBys.value.map { it.id })
        log.remove(1)
        assertEquals(listOf("k|pecos"), repository().driveBys.value.map { it.siteKey })
    }

    @Test
    fun `entries keep their vehicle, and ones saved before vehicles still load`() = runTest {
        file.writeText("""[{"id":1,"siteKey":"k|harlan","description":"ON-RAMP TO EB I-70 FROM HARLAN ST.","lat":39.78313,"lon":-105.0598,"at":1000}]""")
        val log = repository()
        log.add("k|pecos", "ON-RAMP TO WB I-70 from PECOS ST.", GeoPoint(39.78355, -105.0078), Instant.ofEpochSecond(2_000), vehicleId = 7)
        assertEquals(listOf(null, 7L), repository().driveBys.value.map { it.vehicleId })
    }

    @Test
    fun `an unreadable file is kept aside, not lost`() = runTest {
        file.writeText("not json")
        assertTrue(repository().driveBys.value.isEmpty())
        assertTrue(File(dir, "${DriveByRepository.FILE_NAME}.bad").exists())
    }
}
