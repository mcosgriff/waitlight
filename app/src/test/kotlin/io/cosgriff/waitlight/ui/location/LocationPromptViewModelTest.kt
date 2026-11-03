package io.cosgriff.waitlight.ui.location

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.cosgriff.waitlight.data.location.ZipCodes
import io.cosgriff.waitlight.data.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

/** Typing a ZIP, Go and clearing: what's saved and what's refused. Robolectric for DataStore and Log. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class LocationPromptViewModelTest {
    private val dir: File = Files.createTempDirectory("prompt").toFile()
    private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val screen = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val settings = SettingsStore(PreferenceDataStoreFactory.create(scope = storeScope) { File(dir, "settings.preferences_pb") })
    private val zips = ZipCodes({ "80003|39.8254|-105.0644|Arvada" }, Dispatchers.Unconfined)

    @Before fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)

    @After fun tearDown() {
        screen.cancel()
        Dispatchers.resetMain()
        storeScope.cancel()
        dir.deleteRecursively()
    }

    /** A prompt on screen: something collects its field. */
    private fun shown() = LocationPromptViewModel(settings, zips).also { vm -> screen.launch { vm.ui.collect {} } }

    /** The field once [until] holds; DataStore answers on its own thread. */
    private fun LocationPromptViewModel.field(until: (ZipFieldUi) -> Boolean): ZipFieldUi {
        repeat(200) {
            ui.value.takeIf(until)?.let { return it }
            Thread.sleep(10)
        }
        error("The field never got there: ${ui.value}")
    }

    private fun savedZip() = runBlocking { settings.settings.first().zip }

    @Test
    fun `only digits are kept, at most five`() {
        val vm = shown()
        vm.onZipChange("8a0-0 0 3 9")
        assertEquals("80003", vm.field { it.text.isNotEmpty() }.text)
    }

    @Test
    fun `Go with too few digits or an unknown ZIP saves nothing`() {
        val vm = shown()
        var saved = 0
        vm.onZipChange("800")
        vm.go { saved++ }
        assertEquals(ZipError.INCOMPLETE, vm.field { it.error != null }.error)

        vm.onZipChange("90210")
        assertNull(vm.field { it.text == "90210" }.error)
        vm.go { saved++ }
        assertEquals(ZipError.UNKNOWN, vm.field { it.error != null }.error)
        assertEquals(0, saved)
        assertNull(savedZip())
    }

    @Test
    fun `Go with a Colorado ZIP saves it, then clearing forgets it`() {
        val vm = shown()
        var saved = 0
        vm.onZipChange("80003")
        vm.go { saved++ }
        vm.field { it.saved != null }
        assertEquals(1, saved)
        assertEquals("80003", savedZip())

        vm.clearZip()
        assertEquals("", vm.field { it.saved == null }.text)
        assertNull(savedZip())
    }

    @Test
    fun `opened again, the field starts from the saved ZIP`() {
        runBlocking { settings.setZip("80003") }
        assertEquals("80003", shown().field { it.text.isNotEmpty() }.text)
    }
}
