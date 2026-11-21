package io.cosgriff.waitlight.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AtomicJsonFileTest {
    private val dir: File = Files.createTempDirectory("atomic").toFile()
    private val file = File(dir, "things.json")

    @Serializable
    private data class Thing(val name: String, val count: Int)

    private fun things(unreadable: AtomicJsonFile.Unreadable, at: File = file) = AtomicJsonFile(
        file = { at },
        unreadable = unreadable,
        what = "The things",
        io = Dispatchers.Unconfined,
        read = { AtomicJsonFile.json.decodeFromString(Thing.serializer(), it) },
        write = { AtomicJsonFile.json.encodeToString(Thing.serializer(), it) },
    )

    @After
    fun deleteFiles() {
        dir.deleteRecursively()
    }

    @Test
    fun `a missing file loads as nothing`() = runTest {
        assertNull(things(AtomicJsonFile.Unreadable.DISCARD).load())
    }

    @Test
    fun `saves and loads, a save replaces the last one, and no tmp file is left behind`() = runTest {
        val things = things(AtomicJsonFile.Unreadable.DISCARD)
        things.save(Thing("cone", 3))
        assertEquals(Thing("cone", 3), things.load())
        things.save(Thing("van", 1))
        assertEquals(Thing("van", 1), things.load())
        assertEquals(listOf("things.json"), dir.list()!!.toList())
    }

    @Test
    fun `an unreadable file is discarded`() = runTest {
        file.writeText("{\"name\":\"cone\"")
        assertNull(things(AtomicJsonFile.Unreadable.DISCARD).load())
        assertEquals(emptyList<String>(), dir.list()!!.toList())
    }

    @Test
    fun `a file that reads as JSON but not as the thing is unreadable too`() = runTest {
        file.writeText("""{"name":"cone","count":"three"}""")
        assertNull(things(AtomicJsonFile.Unreadable.DISCARD).load())
        assertFalse(file.exists())
    }

    @Test
    fun `an unreadable file kept aside is renamed after itself, and the next save starts afresh`() = runTest {
        file.writeText("not json")
        val things = things(AtomicJsonFile.Unreadable.KEEP_ASIDE)
        assertNull(things.load())
        assertFalse(file.exists())
        assertEquals("not json", File(dir, "things.json.bad").readText())
        things.save(Thing("cone", 3))
        assertEquals(Thing("cone", 3), things.load())
        assertEquals("not json", File(dir, "things.json.bad").readText())
    }

    @Test
    fun `a file that can't be replaced is logged, not thrown, and leaves no tmp file behind`() = runTest {
        // A directory that isn't empty can't be renamed over.
        File(file, "in-the-way").apply { parentFile!!.mkdirs() }.writeText("")
        things(AtomicJsonFile.Unreadable.DISCARD).save(Thing("cone", 3))
        assertEquals(listOf("things.json"), dir.list()!!.toList())
        assertTrue(file.isDirectory)
    }

    @Test
    fun `a file in a directory that doesn't exist is logged, not thrown`() = runTest {
        val nowhere = things(AtomicJsonFile.Unreadable.DISCARD, at = File(File(dir, "missing"), "things.json"))
        nowhere.save(Thing("cone", 3))
        assertNull(nowhere.load())
    }
}
