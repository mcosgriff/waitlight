package io.cosgriff.waitlight.data.aircare

import io.cosgriff.waitlight.domain.FetchOutcome
import io.cosgriff.waitlight.domain.WaitReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

class WaitTimesStoreTest {
    private val dir: File = Files.createTempDirectory("waits").toFile()
    private val file = File(dir, WaitTimesStore.FILE_NAME)
    private val store = WaitTimesStore({ file }, Dispatchers.Unconfined)

    private val times = WaitTimes(
        waits = mapOf(
            38 to (WaitReading.Minutes(13) to denver("2026-11-02T16:14")),
            49 to (WaitReading.MoreThan(60) to denver("2026-11-02T16:16")),
            46 to (WaitReading.NoEstimate to denver("2026-11-02T16:15")),
        ),
        lastOutcome = FetchOutcome.OFFLINE,
    )

    @After
    fun deleteFiles() {
        dir.deleteRecursively()
    }

    @Test
    fun `nothing stored loads as nothing`() = runTest {
        assertNull(store.load())
    }

    @Test
    fun `saves and loads every kind of reading, its time and the outcome`() = runTest {
        store.save(times)
        assertEquals(times, store.load())
    }

    @Test
    fun `a save replaces the last one`() = runTest {
        store.save(times)
        val next = WaitTimes(mapOf(38 to (WaitReading.Minutes(0) to denver("2026-11-02T16:30"))), FetchOutcome.OK)
        store.save(next)
        assertEquals(next, store.load())
    }

    @Test
    fun `a corrupt file loads as nothing`() = runTest {
        file.writeText("{\"lastOutcome\":\"OK\",\"waits\":[{\"id\":38")
        assertNull(store.load())
    }

    @Test
    fun `an outcome it doesn't know loads as nothing`() = runTest {
        file.writeText("""{"lastOutcome":"SIDEWAYS","waits":[]}""")
        assertNull(store.load())
    }

    @Test
    fun `a station no longer in Stations is dropped`() = runTest {
        val updated = denver("2026-11-02T16:14").toEpochMilli()
        file.writeText(
            """{"lastOutcome":"OK","waits":[
                {"id":38,"kind":"minutes","minutes":13,"updatedAt":$updated},
                {"id":9999,"kind":"minutes","minutes":5,"updatedAt":$updated}
            ]}""",
        )
        assertEquals(setOf(38), store.load()?.waits?.keys)
    }

    @Test
    fun `a reading that can't be a wait is dropped`() = runTest {
        val updated = denver("2026-11-02T16:14").toEpochMilli()
        file.writeText(
            """{"lastOutcome":"OK","waits":[
                {"id":38,"kind":"minutes","minutes":-4,"updatedAt":$updated},
                {"id":49,"kind":"moreThan","updatedAt":$updated},
                {"id":46,"kind":"noEstimate","updatedAt":$updated}
            ]}""",
        )
        assertEquals(setOf(46), store.load()?.waits?.keys)
    }

    @Test
    fun `a file that can't be written is logged, not thrown`() = runTest {
        val nowhere = WaitTimesStore({ File(File(dir, "missing"), WaitTimesStore.FILE_NAME) }, Dispatchers.Unconfined)
        nowhere.save(times)
        assertNull(nowhere.load())
    }
}
