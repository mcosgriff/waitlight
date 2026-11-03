package io.cosgriff.waitlight.widget

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.cosgriff.waitlight.data.location.HereSource
import io.cosgriff.waitlight.data.location.LastLocationStore
import io.cosgriff.waitlight.data.location.LocationSource
import io.cosgriff.waitlight.data.location.ZipCodes
import io.cosgriff.waitlight.data.settings.SettingsStore
import io.cosgriff.waitlight.domain.GeoPoint
import io.cosgriff.waitlight.domain.Here
import io.cosgriff.waitlight.domain.ZipPlace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

/**
 * The widget's "here" ([HereSource.current]), worked out in the background with no location permission (Robolectric's
 * default): the saved ZIP code, or nowhere, so the widget asks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class WidgetHereTest {
    private val dir: File = Files.createTempDirectory("widget-here").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val settings = SettingsStore(
        PreferenceDataStoreFactory.create(scope = scope) { File(dir, "settings.preferences_pb") },
    )
    private val lastLocation = LastLocationStore({ File(dir, LastLocationStore.FILE_NAME) }, Dispatchers.Unconfined)
    private val here = HereSource(
        LocationSource(RuntimeEnvironment.getApplication(), lastLocation),
        settings,
        ZipCodes({ ZIPS }, Dispatchers.Unconfined),
        scope,
    )

    @After
    fun tearDown() {
        scope.cancel()
        dir.deleteRecursively()
    }

    @Test
    fun `with location off, the widget uses the saved ZIP code`() = runTest {
        settings.setZip("80003")
        assertEquals(Here.Zip(ARVADA), here.current())
    }

    @Test
    fun `with location off and no ZIP code, the widget has nowhere and asks`() = runTest {
        assertNull(here.current())
    }

    @Test
    fun `a fix kept from before location was turned off isn't used`() = runTest {
        lastLocation.save(GeoPoint(39.9, -105.1))
        assertNull(here.current())
    }

    private companion object {
        const val ZIPS = "80003|39.8254|-105.0644|Arvada"
        val ARVADA = ZipPlace("80003", GeoPoint(39.8254, -105.0644), "Arvada")
    }
}
