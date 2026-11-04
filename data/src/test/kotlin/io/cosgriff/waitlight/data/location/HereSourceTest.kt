package io.cosgriff.waitlight.data.location

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.cosgriff.waitlight.data.settings.SettingsStore
import io.cosgriff.waitlight.domain.GeoPoint
import io.cosgriff.waitlight.domain.Here
import io.cosgriff.waitlight.domain.ZipPlace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** "Here" through [HereSource], with a fake phone: the fix, the saved ZIP code, and the permission between them. */
@OptIn(ExperimentalCoroutinesApi::class)
class HereSourceTest {
    /** A phone whose permission and fix the test sets. Like Android's, a permission change shows only on [recheck]. */
    private class FakePhoneFix(var allowed: Boolean, var fix: GeoPoint?) : PhoneFix {
        private val state = MutableStateFlow(allowed)
        var finds = 0

        override val permitted: StateFlow<Boolean> = state.asStateFlow()

        override fun recheck() {
            state.value = allowed
        }

        override suspend fun find(): GeoPoint? {
            finds++
            return fix.takeIf { allowed }
        }
    }

    @get:Rule val folder = TemporaryFolder()

    private val settingsScope = CoroutineScope(UnconfinedTestDispatcher())
    private val settings by lazy {
        SettingsStore(PreferenceDataStoreFactory.create(scope = settingsScope) { File(folder.root, "settings.preferences_pb") })
    }
    private val lastFix by lazy { LastLocationStore({ File(folder.root, LastLocationStore.FILE_NAME) }, Dispatchers.Unconfined) }

    @After fun tearDown() = settingsScope.cancel()

    private fun TestScope.hereSource(phone: PhoneFix) = HereSource(
        phone,
        lastFix,
        settings,
        ZipCodes({ ZIPS }, Dispatchers.Unconfined),
        CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
    )

    @Test
    fun `with location allowed, the app coming to the front finds the phone, and the phone is here`() = runTest {
        val source = hereSource(FakePhoneFix(allowed = true, fix = PHONE))
        source.onForeground()
        assertEquals(Here.Device(PHONE, ARVADA), source.here.first { it != null })
        assertEquals(PHONE, lastFix.load())
    }

    @Test
    fun `location allowed while the list is showing is picked up when the app comes back`() = runTest {
        settings.setZip("80003")
        val phone = FakePhoneFix(allowed = false, fix = PHONE)
        val source = hereSource(phone)
        source.onForeground()
        assertEquals(Here.Zip(ARVADA), source.here.first { it != null })
        assertEquals(0, phone.finds)

        phone.allowed = true // the permission dialog, answered
        source.onForeground()
        assertEquals(Here.Device(PHONE, ARVADA), source.here.first { it is Here.Device })
    }

    @Test
    fun `while the app is in front with no fix, it looks again every 30 seconds, and stops in the background`() = runTest {
        val phone = FakePhoneFix(allowed = true, fix = null) // location services off, say
        val source = hereSource(phone)
        source.onForeground()
        assertEquals(1, phone.finds)
        advanceTimeBy(30_001)
        assertEquals(2, phone.finds)

        phone.fix = PHONE // turned on from the quick-settings shade, which doesn't pause the app
        advanceTimeBy(30_000)
        assertEquals(Here.Device(PHONE, ARVADA), source.here.first { it is Here.Device })
        advanceTimeBy(60_000)
        assertEquals(3, phone.finds) // found: no more looking

        val other = FakePhoneFix(allowed = true, fix = null)
        val inBackground = hereSource(other)
        inBackground.onForeground()
        inBackground.onBackground()
        advanceTimeBy(60_000)
        assertEquals(1, other.finds)
    }

    @Test
    fun `location revoked forgets the fix, kept one too`() = runTest {
        settings.setZip("80003")
        val phone = FakePhoneFix(allowed = true, fix = PHONE)
        val source = hereSource(phone)
        source.onForeground()
        source.here.first { it is Here.Device }

        phone.allowed = false
        source.onForeground()
        assertEquals(Here.Zip(ARVADA), source.here.first { it is Here.Zip })
        assertNull(lastFix.load())
    }

    @Test
    fun `the widget uses the fix kept on disk, since a background app gets none`() = runTest {
        lastFix.save(PHONE)
        val phone = FakePhoneFix(allowed = true, fix = null)
        assertEquals(Here.Device(PHONE, ARVADA), hereSource(phone).current())
        assertEquals(0, phone.finds)
    }

    @Test
    fun `with location off, the widget uses the saved ZIP code`() = runTest {
        settings.setZip("80003")
        assertEquals(Here.Zip(ARVADA), hereSource(FakePhoneFix(allowed = false, fix = PHONE)).current())
    }

    @Test
    fun `with location off and no ZIP code, the widget has nowhere and asks`() = runTest {
        assertNull(hereSource(FakePhoneFix(allowed = false, fix = PHONE)).current())
    }

    @Test
    fun `a fix kept from before location was turned off isn't used`() = runTest {
        lastFix.save(PHONE)
        assertNull(hereSource(FakePhoneFix(allowed = false, fix = PHONE)).current())
    }

    private companion object {
        const val ZIPS = "80003|39.8254|-105.0644|Arvada"
        val ARVADA = ZipPlace("80003", GeoPoint(39.8254, -105.0644), "Arvada")
        val PHONE = GeoPoint(39.9, -105.1)
    }
}
