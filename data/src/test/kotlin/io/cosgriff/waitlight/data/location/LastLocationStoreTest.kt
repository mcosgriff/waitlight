package io.cosgriff.waitlight.data.location

import io.cosgriff.waitlight.domain.GeoPoint
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LastLocationStoreTest {
    @get:Rule val folder = TemporaryFolder()

    private val file get() = File(folder.root, LastLocationStore.FILE_NAME)

    @Test
    fun `keeps the last location`() = runTest {
        val store = LastLocationStore({ file }, StandardTestDispatcher(testScheduler))
        assertNull(store.load())
        store.save(GeoPoint(39.8277, -105.0656))
        assertEquals(GeoPoint(39.8277, -105.0656), store.load())
        store.save(GeoPoint(40.5, -105.0))
        assertEquals(GeoPoint(40.5, -105.0), store.load())
    }

    @Test
    fun `forgets it when cleared`() = runTest {
        val store = LastLocationStore({ file }, StandardTestDispatcher(testScheduler))
        store.save(GeoPoint(39.8277, -105.0656))
        store.clear()
        assertNull(store.load())
    }

    @Test
    fun `an unreadable or impossible file reads as no location`() = runTest {
        val store = LastLocationStore({ file }, StandardTestDispatcher(testScheduler))
        file.writeText("not a place")
        assertNull(store.load())
        file.writeText("123.0,-105.0")
        assertNull(store.load())
    }
}
